# Local Docker setup

## Current stage

Docker configuration is prepared for all nine services, RabbitMQ, and Kafka.
Kafka payment setup and verification are documented in [Kafka payments](kafka-payments.md).
Media storage, image validation, and its integration with Accounts and Marketplace
are documented in [Media service](media-service.md). See
[architecture decisions](architecture-decisions.md) for service ownership and
transaction boundaries.

Each service has a Java 17 multi-stage Dockerfile and its own `.dockerignore`.
The Maven build runs inside Docker; only the executable JAR is copied into the
non-root Java runtime image. Logs go to stdout/stderr.

## Configure

Use Docker Desktop with the Linux engine running and Docker Compose v2.
From the repository root, copy `.env.example` to `.env` and fill its blank values:

```powershell
Copy-Item .env.example .env
```

Do this only when `.env` does not already exist. `.env` is Git-ignored.

- `POSTGRES_PASSWORD`: local container database password.
- `JWT_SECRET`: Base64 encoding of at least 32 cryptographically random bytes.
- `INTERNAL_API_TOKEN`: a random value at least 32 characters long.
- `RABBITMQ_PASSWORD`: password for the non-guest RabbitMQ user.
- `SMTP_HOST`, credentials and TLS settings: your SMTP provider configuration.
- `PGADMIN_DEFAULT_PASSWORD`: local pgAdmin password. Compose interpolates this
  required variable even when the optional tools profile is not started.
- `IMAGE_TAG`: defaults to `local`; use a version or commit tag for releases.
- `KAFKA_CLUSTER_ID`: stable URL-safe Base64 UUID for the persisted KRaft cluster.
- `PAYMENT_KAFKA_CONCURRENCY`: defaults to 3; must not exceed payment pool core size.
- `MEDIA_PUBLIC_BASE_URL`: browser-facing Media origin stored in image URLs;
  defaults to `http://localhost:8088`. Set it empty once a gateway serves
  `/api/media/**`.

The checked-in example contains no working credentials. Compose injects only the
environment entries assigned to each service. No environment file is copied into
an image. `AUTH_COOKIE_SECURE=false` is for this local HTTP setup; HTTPS deployments
must use secure cookies.

## Validate and build (does not start the project)

```powershell
docker compose --profile tools config --quiet
docker compose build
```

Build only one service:

```powershell
docker compose build marketplace-service
```

Dockerfiles currently use `-Dmaven.test.skip=true`: legacy monolith tests still
need migration. This skips both test compilation and execution. Image builds are
not test evidence; restore normal repository tests and a separate mandatory CI
test gate before releases.

## Networking

| Service | Local URL / port |
|---|---|
| Gateway (open this) | http://localhost:8080 |
| Frontend (internal) | not published |
| Accounts | http://localhost:8081 |
| Chat | http://localhost:8082 |
| Marketplace | http://localhost:8083 |
| Favorites | http://localhost:8084 |
| Notification | http://localhost:8085 |
| Moderation | http://localhost:8086 |
| Audit | http://localhost:8087 |
| Media | http://localhost:8088 |
| RabbitMQ management | http://localhost:15672 |
| pgAdmin (tools profile) | http://localhost:5050 |

Open `http://localhost:8080` in a browser. The gateway serves the pages and every
`/api/**` and `/auth/user/**` call from that same origin; see `gateway.md`. The
per-service ports stay published as direct debugging entry points, and they are
how `scripts/Test-MediaService.ps1` reaches Media without a token-free path at
the edge.

Published ports bind to loopback for local development. Internal calls use Compose
DNS names, such as `http://accounts-service:8081`, `postgres_db:5432`, and
`rabbitmq:5672`. PostgreSQL and AMQP are not published to the host. In pgAdmin,
register a server using hostname `postgres_db`, port 5432, and the database user
and password from `.env`.

PostgreSQL 18 stores its data under the mounted `/var/lib/postgresql` directory.
Named volumes also persist RabbitMQ and pgAdmin state. The RabbitMQ hostname is
fixed so its node identity remains stable across container recreation.

## Before the first full application run

1. Configure Kafka as described in `kafka-payments.md` and start its healthy broker.
2. Bootstrap the database once per fresh volume:
   ```powershell
   docker compose up -d postgres_db
   powershell -ExecutionPolicy Bypass -File .\scripts\Bootstrap-Database.ps1
   ```
   This creates the `used` role and database, applies the full schema (dumped
   from the host `used` database), transfers ownership, and runs every manual DDL
   file (`media-assets.sql`, `notification-processed-events.sql`,
   `notification-indexes.sql`, `payment-indexes.sql`,
   `chat-conversation-visibility.sql`). It is idempotent.
3. Gateway routing is implemented: `gateway` owns port 8080 and maps every
   browser path family to its service. Relative API requests no longer 404.
   Verify with `scripts/Test-Gateway.ps1`.
4. Configure SMTP (or accept the placeholder; notification health will be DOWN
   but request handling still works) and verify application health and service
   communication.

Database and broker health checks gate dependent service startup. They do not
establish schema readiness or application readiness. HTTP peers are not startup
dependencies; their availability must be checked during integration verification.
Changing database or RabbitMQ initialization passwords in `.env` does not rotate
credentials already stored in persistent volumes.

## Later runtime commands

After the prerequisites above are complete:

```powershell
docker compose up -d
docker compose --profile tools up -d pgadmin
docker compose ps
docker compose logs --tail 100 -f marketplace-service
docker compose down
```

`down` preserves named volumes. `down -v` deletes their data; it is not a routine
restart command.

## Troubleshooting startup

**`Unable to determine Dialect without JDBC metadata`.** The JPA services print this
when Hibernate cannot read JDBC metadata, and the most common cause is that
PostgreSQL is not accepting connections - not a missing `spring.datasource.url`.
Read further up the log for the real error:

```text
WARN  HHH000247: ErrorCode: 0, SQLState: 57P03
WARN  FATAL: the database system is not yet accepting connections
```

`57P03` means PostgreSQL is still in crash recovery after an unclean shutdown; a
large data directory can take minutes. Check it, then restart the app once:

```powershell
docker compose exec -T postgres_db pg_isready -U used -d used
docker compose restart media-service
```

**A service that failed during database recovery keeps failing.** Spring Boot fails
its context once and the container restarts, but a service started while the
database was unavailable needs one explicit restart after recovery finishes.

**`Remote host terminated the handshake` during `docker compose build`.** That is a
transient Maven Central TLS failure inside the build container, not a code problem.
Rebuild the affected service; layers already built are cached.

## Dev-only tuning (do not ship)

- `JAVA_TOOL_OPTIONS=-Xmx384m -Xms128m` is set for every service in
  `x-app-environment`. The Docker Desktop WSL2 VM has ~7.7 GiB and each JVM
  would otherwise default to ~1.9 GiB heap, OOM-killing the stack. 384 MB is
  enough for Spring Boot 4 + JPA + Kafka client. Raise it only if a service
  genuinely needs more.
- The dev Postgres runs with `fsync=off`, `full_page_writes=off`, and
  `synchronous_commit=off`. This makes recovery and bulk loads fast on the WSL2
  disk (fsync otherwise stalls for 10–60 s per file). RDS keeps the defaults.

## Verification recorded for this change

- Compose configuration validation passed with temporary placeholder values,
  including the tools profile. These values were not used to start services.
- After Docker Desktop was started, all nine application images built successfully;
  PostgreSQL and RabbitMQ images pulled successfully.
- First live Compose startup: PostgreSQL and RabbitMQ healthy; frontend `/` returned
  HTTP 200 and Audit `/actuator/health` returned HTTP 200 with status UP.
- Container database `used` exists but has zero public tables. Host database data
  has not been imported. SMTP uses a non-delivering placeholder for this check.
- `spring-boot-starter-restclient` was added to the six HTTP-client services; those
  images were rebuilt and the services started.
- Kafka was added and Marketplace was rebuilt with payment consumption. Marketplace
  health is UP, both payment topics exist with three partitions, and three consumers
  are assigned one partition each.
- Media now has datasource configuration, image storage, security, and validation.
  Its image was rebuilt and the service starts with health UP. The `media_assets`
  table was applied to the container database and the full direct HTTP contract is
  verified by `scripts/Test-MediaService.ps1` (201/200/304/400/401 plus a SHA-256
  round-trip check); see `media-service.md`.
- A Media start after the PostgreSQL crash-recovery incident failed with a
  misleading `Unable to determine Dialect without JDBC metadata` error. The true
  cause was `SQLState 57P03` from PostgreSQL; a restart after recovery cleared it.
  The datasource configuration itself was correct.
- Media's 14 tests pass, plus 4 Accounts and 11 Marketplace image tests through an
  isolated harness (the full module runs are still blocked by stale monolith-era
  tests). Those are unit/integration results and do not establish a business
  workflow. No end-to-end purchase, profile, or listing simulation has run.
