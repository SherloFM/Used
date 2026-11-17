# Architecture decisions

Last updated: 2026-11-16

This file is the durable record of the agreed structure. Read it before starting
API or messaging work. It is intentionally short enough to be carried into every
session handoff.

## Service topology

The monolith is split into nine independently deployable Spring Boot services.
The root POM is an aggregator only.

| Service | Port | Owns |
|---|---|---|
| accounts-service | 8081 | users, credentials, profiles, authentication |
| chat-service | 8082 | conversations, messages |
| marketplace-service | 8083 | listings, categories, payments, wallet, transactions |
| favorites-service | 8084 | favorites |
| notification-service | 8085 | notifications, email delivery |
| moderation-service | 8086 | listing reports |
| audit-service | 8087 | audit log |
| frontend-service | 8080 (internal) | static UI and page routing |
| media-service | 8088 | image bytes and metadata |
| gateway | 8080 (browser) | reverse proxy; owns no data |

Each service owns its tables. Cross-service references are scalar IDs. Shared
database tables are not joined across service boundaries.

## Synchronous HTTP

Used for immediate request/response reads and commands.

- Internal calls use `X-Internal-Token` with the shared `INTERNAL_API_TOKEN`.
- Server filters require a token of at least 32 characters and grant
  `ROLE_INTERNAL`.
- Clients use `RestClient` with explicit bean qualifiers, 2-second connect and
  3-second read timeouts, and no automatic retries.
- A `404` means "missing" only where the contract says so. Other failures
  propagate; they must not be converted into fake missing data.
- Known internal endpoints:
  - Accounts: `GET /api/internal/users/{id}`
  - Marketplace: `GET /api/internal/listings/{id}`,
    `POST /api/internal/listings/{id}/delist`
  - Notification: internal push/broadcast endpoints
  - Media: `POST /api/internal/media` (multipart upload)
  - Favorites: listing-update/removal callbacks removed in 8D; RabbitMQ consumers
    handle listing consistency.

## Messages

### RabbitMQ — listing lifecycle and status consistency

RabbitMQ is the asynchronous backbone for keeping listing state and everything
that depends on it consistent:

- listing created / updated / sold / delisted events,
- propagation of listing status to favorites, notifications, and moderation,
- durable listing-removal cleanup and retries,
- the moderation delist saga described below.

RabbitMQ is not used for payment work distribution.

Topology (produced by Marketplace and Moderation; consumed by Moderation,
Favorites, and Notification):

- exchange `listing.commands` (direct) to queue `marketplace.listing-delist`
  with routing key `listing.delist`, dead-lettered through
  `listing.commands.dlx` to `marketplace.listing-delist.dlq`;
- exchange `listing.events` (topic). It carries both the delist saga result and
  the listing lifecycle facts:
  - queue `moderation.listing-delist-results`, routing key
    `listing.delist.result`, dead-lettered to
    `moderation.listing-delist-results.dlq`;
  - queue `favorites.listing-events`, routing keys `listing.updated`,
    `listing.sold`, `listing.delisted`, dead-lettered to
    `favorites.listing-events.dlq`;
  - queue `notification.listing-events`, routing keys `listing.sold`,
    `listing.delisted` only (Notification has nothing to say about an update),
    dead-lettered to `notification.listing-events.dlq`.

Routing keys are per event type so the broker does the filtering and each
consumer binds only what it acts on. `listing.created` is defined in the
contract but deliberately not published yet: nothing consumes it, and with
mandatory publishing every unmapped create would log a routing error.

The consumer owns its queue and bindings; the producer owns the exchange. Both
sides declare the exchange idempotently, so either can start first.

All queues are quorum queues, including the dead-letter queues. Dead-letter
queues carry a 7-day `x-message-ttl`, `x-max-length` of 10,000, and
`x-overflow=drop-head`, so unactionable failures expire instead of accumulating.
Each queue dead-letters to its own routing key (`<queue>.dead`), because one key
shared by several queues would make failures indistinguishable.

Messages are JSON and persistent.

### Message type ids

`__TypeId__` carries a logical contract name, never a Java class name:
`listing.event`, `listing.delist.command`, `listing.delist.result`.

This is not cosmetic. Each service owns its own copy of every contract, so a
class name from the producer cannot exist in the consumer's process. Measured
failures without the mapping: `not in the trusted packages: [java.util,
java.lang]`, `Class not found`, and, when the header is omitted,
`java.util.LinkedHashMap`. Each service maps the ids to its own classes with
`DefaultJacksonJavaTypeMapper.setIdClassMapping`, and no trusted-package
configuration is needed.

The converter is `JacksonJsonMessageConverter` (Jackson 3). The older
`Jackson2JsonMessageConverter` is deprecated and marked for removal in Spring
AMQP 4.1.2, and it throws `InvalidDefinitionException: Java 8 date/time type
java.time.Instant not supported by default` on the event's `Instant` fields.

### Publishing guarantees

- Listing lifecycle events are published **asynchronously**. The publish does
  not block the request thread, and the broker's answer is logged from
  `CorrelationData.getFuture()`: a nack, a lost connection, or a confirmed but
  unroutable message. The documented cost: the caller cannot know whether an
  event reached a queue. Durability across a lost publish needs the outbox.
- The moderation delist command and result publishers still **block** on the
  confirm, because those listeners need the boolean to decide whether to reject
  or to leave the report `DELISTING`.
- Publishing is mandatory with correlated confirms. An unroutable message is
  still acknowledged by the broker; the returned message is the failure signal,
  so any retry logic must check both the confirm and
  `CorrelationData.getReturned()`.

### Kafka — payment load

Kafka distributes payment jobs across Marketplace workers:

- topic: `payment-requests`
- key: `listingId` (preserves per-listing ordering and partitioning)
- consumer group: `marketplace-payment-workers`
- the worker commits its Kafka offset only after the database transaction
  commits.

The payment execution core stays inside Marketplace: payment state, wallet
balances, the listing sale, and payment/transaction records remain atomic in one
local transaction. Marketplace keeps its existing payment thread pool for the
actual work.

Implemented payment-only Kafka flow (2026-11-16): listener containers run directly
on `paymentExecutor`; payment execution is synchronous within each listener.
No nested task/future is submitted. Consumer concurrency cannot exceed pool size.
The source and `.DLT` topics have three partitions. Manual-immediate acknowledgments
follow committed processing. Non-retryable argument/state and deserialization
errors go directly to the DLT; other failures get three retries five seconds apart.
Confirmed DLT delivery plus conditional failure of a matching pending wallet
payment precede committing a recovered offset (`commitRecovered=true`,
`ackAfterHandle=false`). DLT publication/DB recovery failures leave offsets
uncommitted. Raw malformed payloads are preserved. Stale pending wallet rows are
republished by a bounded rotating scan without schema additions. Terminal rows are
not reopened. See `kafka-payments.md` for limitations and verification evidence.
RabbitMQ continues all existing listing lifecycle and moderation messaging.

## Security

- No Keycloak and no OAuth2 resource server.
- HS256 JWTs signed with a shared `JWT_SECRET`.
- The immutable user ID is the JWT subject.
- The JWT is stored in the `used_auth` HttpOnly cookie and is never exposed to
  browser JavaScript.
- CSRF uses the `used_csrf` cookie and the `X-XSRF-TOKEN` header. The frontend
  bootstraps the token from `/auth/user/csrf`; the cookie remains HttpOnly.
- CORS is credentialed and restricted to explicit `FRONTEND_ORIGINS`. It is now a
  local-dev fallback: browsers reach the services through the gateway, so the
  production path needs no CORS at all.
- Accounts alone checks whether an account still exists, is verified, and is not
  banned. Other services validate JWT claims only, so a ban propagates when the
  token expires.

### Browser edge

`gateway` (nginx, port 8080) is the single browser origin. It exists because the
frontend's `authenticatedFetch` sends `credentials: 'include'` with
`X-XSRF-TOKEN`, and `EventSource('/api/notifications/stream')` cannot set headers
or credentials at all. One origin keeps the `used_auth` cookie host-scoped,
keeps CSRF unchanged, and avoids `SameSite=None`.

- Routes by path prefix to the owning service; `/api/admin/users` goes to
  Accounts while `/api/admin/reports` goes to Moderation.
- `/api/internal/**` and `/actuator/**` return `404`. The service-to-service
  surface is not browser-reachable by any path.
- SSE is unbuffered; `/ws` upgrades; proxy read timeout is 120 s with 1 h for
  those two.
- It is a router only. It owns no data and performs no authentication; every
  service still validates the JWT itself.
- `upstream` names are resolved at config load, so a service recreated with a new
  address needs `docker compose restart gateway`.
- `depends_on` uses `service_started`, not `service_healthy`: the edge needs the
  names to resolve, and gating on health would hold the UI closed for the length
  of a cold Spring start.

See `gateway.md`.

## Consistency model

### Moderation delist saga (implemented in 8B)

Resolving a report and delisting its listing live in two services, so they
cannot share a database transaction. RabbitMQ carries the intent:

1. `DELISTING` report status.
2. `resolveReport` commits `DELISTING`, the acting admin, and the notes before
   any remote call. A Spring application event is handled by
   `DelistCommandDispatcher` with `@TransactionalEventListener(AFTER_COMMIT)`,
   so a rolled-back decision never sends a command.
3. Marketplace's internal delist is idempotent:
   - `ACTIVE` → delist once (email, audit, favorites),
   - `DELISTED` → `ALREADY_DELISTED`, no repeated side effects,
   - `SOLD` → `SOLD`,
   - missing → `NOT_FOUND`.
4. Marketplace publishes `ListingDelistResult` after the delist transaction
   returns. `ReportService.applyDelistResult` only acts when the report is still
   `DELISTING`, so duplicate results are harmless. `DELISTED`,
   `ALREADY_DELISTED`, `NOT_FOUND`, and `SOLD` finalize the report as
   `ACTION_TAKEN` with a note; `FAILED` leaves it `DELISTING`.
5. `DelistRetryScheduler` republishes commands for reports that have been
   `DELISTING` for longer than the stale threshold. This is the retry loop. The
   dead-letter queue is quarantine and evidence, not a retry mechanism.

Publishing is at-least-once, and every step is idempotent, so a crash between
the two services converges on the next sweep. The admin sees `DELISTING` instead
of `ACTION_TAKEN` until Marketplace confirms.

An outbox table may be required for durable publishing. Because schema changes
are currently restricted to Media, adding one requires explicit approval.

### Listing lifecycle events (implemented in 8C)

Marketplace announces every committed listing change exactly once through
`ListingLifecycle` (`updated`, `sold`, `delisted`). Nobody calls Favorites or
Notification to report a change any more.

1. The call site mutates the listing and calls the lifecycle inside the same
   transaction.
2. `ListingEventDispatcher` is a `@TransactionalEventListener` with
   `phase = AFTER_COMMIT` and `fallbackExecution = false`, so a rolled-back
   change can never be announced. `ListingLifecycle` refuses to publish when no
   transaction synchronization is active and logs the mistake, because with
   `fallbackExecution = false` such an event would otherwise vanish silently.
3. The event snapshots the row (title, price, seller, status, image) at publish
   time. Consumers never call Marketplace back to render their own message, so a
   notification no longer depends on Marketplace being up.
4. Mutation paths use `saveAndFlush` so `@UpdateTimestamp` is applied before the
   event is built; the event therefore carries the timestamp the seller sees.
   Status flips through the bulk `updateStatusIfCurrent` do not bump it, which is
   why `occurredAt` is the ordering token and `updatedAt` is only display data.
5. `sold()` forces status `SOLD` instead of trusting a possibly stale entity, and
   the sale paths re-read the listing after the atomic `ACTIVE -> SOLD` claim.

Consumer behaviour:

- Favorites: `updated` pushes to every saver except the seller; `sold` and
  `delisted` push, delete the favorites, and email the savers after commit. The
  favorites are deleted before the emails are sent, so a redelivered event finds
  nothing and repeats nothing.
- Notification: `sold` sends the seller's in-app push and email; `delisted`
  emails only for `ADMIN` and `MODERATION` sources, preserving the rule that an
  owner's own cancellation sends no seller email.

### Duplicate suppression in Notification

Notification owns the table `processed_listing_events` (primary key `event_id`,
plus `event_type`, `listing_id`, `processed_at`). The event id is claimed in a
`REQUIRES_NEW` transaction **before** any push or email is sent, so a
redelivered message cannot email the seller twice; a failure releases the claim
so the retry can run. The remaining window is a crash between the claim commit
and the email, which skips an email instead of repeating it - the deliberate
trade, because a missing "your item sold" is recoverable in the app and a
duplicate is not.

This is a local Notification table, not a second database, and it is the only
schema addition in 8C. It was explicitly requested as DB deduplication; Redis was
not used because no Redis process exists and Docker is deferred.

DDL is applied manually:
`psql -d used -f src/main/resources/db/notification-processed-events.sql`.

### Favorites

`addFavorite` uses a listing snapshot fetched over HTTP. The old `FOR UPDATE`
add-versus-sale atomicity is intentionally gone; the listing lifecycle events are
what reconcile a favorite with a sale or delist. A favorite can still briefly
outlive a sale or delist between the commit and the event being consumed, which is
why the removal path is idempotent.

### Payments

Payment execution, wallet balances, listing sale, and payment/transaction
records remain atomic inside Marketplace. Kafka distributes work; it does not
split the transaction.

### Media

Media owns `media_assets` (bytes, content type, size, SHA-256, uploader id) and
serves immutable images at `/api/media/{id}`. Accounts and Marketplace authenticate
the user and check ownership first, then upload with the internal token, then store
the returned URL in the existing `imgUrl` column and clear inline bytes. The
external upload is intentionally outside their database transaction so a slow
Media call does not hold a row lock.

There is no distributed transaction: a stored asset can become unreferenced if the
owner's save fails, and replacement leaves the old asset behind. Both are accepted
cleanup gaps. Existing inline images keep rendering through the frontend fallback
until migrated. Media performs no re-encoding, EXIF stripping, or malware scanning.
See `media-service.md`.

## Constraints

- One database and one schema. No schema changes except those required by Media,
  unless explicitly approved. Approved exceptions: `processed_listing_events`
  (Notification, 8C dedup).
- Java packages are lowercase.
- Prefer simple plain Java objects; records where they help.
- Do not add Docker or a gateway until their dedicated phase.
- Compilation success does not prove runtime integration. Report compile results,
  isolated tests, and simulation evidence separately.
- Full application tests are still partly blocked by stale monolith-era test
  sources and unresolved `DB_*` environment variables.

## Current state (2026-11-16)

### EC2/Jenkins preparation (2026-11-17)

- User approved hosting Jenkins on the same 8-GiB Amazon Linux 2023 instance.
- Deployment uses nine Java 17 JARs under independent systemd units, host nginx
  on port 80, and Jenkins on Java 21 at port 8090. Database, RabbitMQ and Kafka
  run with persistent Docker volumes and loopback host bindings. EC2 PostgreSQL
  retains durability defaults. See `docs/ec2-jenkins.md` and `deploy/ec2/`.
- Jenkins builds/tests the nine services sequentially and deploys JARs with
  health-gated rollback. The unproven Selenium module is explicitly excluded.
  AWS installation and an observed Jenkins run remain pending user setup.
- The EC2 schema baseline is a schema-only snapshot of the existing database,
  not a migration redesign; no user data is shipped. Initial import refuses an
  already populated database. Jenkins never resets or migrates the database.

- Accounts, Marketplace, Favorites, Chat, Notification, Audit, and Moderation have
  JWT/cookie security and internal-token wiring.
- Chat is fully migrated, including WebSocket authentication and STOMP rules.
- Moderation is fully migrated: scalar report IDs, HTTP clients, Marketplace delist
  endpoint, JWT cookie authentication, admin-route authorization, and service tests.
- RabbitMQ foundation (8A) is implemented in Moderation and Marketplace:
  dependency, connection settings, JSON converter, quorum topology, publisher
  confirms and returns, and the delist contracts.
- The moderation delist saga (8B) is implemented: `DELISTING`, after-commit
  command dispatch, idempotent Marketplace delist with an outcome, result
  listener, and the retry scheduler. The internal delist endpoint returns 204 for
  an already delisted listing because retries must be safe.
- Listing lifecycle events (8C) are implemented: `ListingLifecycle` as the single
  producer API, after-commit dispatch, the logical `listing.event` type id,
  Favorites and Notification consumers, and Notification's
  `processed_listing_events` dedup table. The seller email and in-app push now
  come from the `listing.sold` event, and a wallet sale now also pushes in-app.
- Evidence for 8C: the reactor compiles, isolated suites pass (Marketplace 57,
  Moderation 46, Favorites 13, Notification 16), and a live-broker probe using the
  services' own topology and converters passes 31 checks: per-queue routing,
  logical type ids resolving to each service's own record, redeclaration with
  matching arguments, and rejection dead-lettering to the right DLQ.
- 8D cleanup is implemented: removed Moderation's HTTP delist client method and
  its exceptions, Marketplace's Favorites HTTP publisher/client/configuration,
  Favorites' internal callback controller and service methods, and Marketplace's
  unused email methods/DTOs. COD email calls and required HTTP listing reads remain.
  Favorites retains internal-token security and its short-secret rejection test.
- Accounts explicitly maps `UserProfile` to the existing `user_profile` table
  with user approval; this changes no database schema. The earlier claim that
  the camel-case annotation alone proved a runtime failure was too strong:
  Hibernate's physical naming strategy can already translate camel case.
- 8D verification: all 10 reactor modules compile; isolated suites pass with
  Marketplace 57, Moderation 46, Favorites 14, Notification 16 tests. Moderation's
  temporary harness initially retained an outdated test; refreshing that copy
  resolved its compilation failure. These are isolated results, not full-module
  or end-to-end integration results. No new simulation or live-broker run was
  performed for 8D; the 31-check broker evidence above belongs to 8C.
- `processed_listing_events` DDL must be applied manually; no auto-DDL is
  configured anywhere.
- Media is now implemented: it owns `media_assets`, validates real JPEG/PNG bytes,
  serves immutable images, and is used by Accounts profile uploads and Marketplace
  listing uploads. Both callers keep the previous image if Media fails and store
  only `imgUrl` for new uploads. Frontend templates prefer `imgUrl` with a Base64
  fallback for old rows.
- Media runtime evidence (2026-11-16): `scripts/Test-MediaService.ps1` returned
  `MEDIA_SMOKE_OK ... status=201 retrieval=200 etag=304 stale-etag=200 invalid=400
  unauthorized=401`, the downloaded bytes matched the uploaded SHA-256, and
  `media_assets` held the row. Media tests pass (14).
- The first Media container start failed with `Unable to determine Dialect without
  JDBC metadata`. That message was a symptom, not a configuration gap: the log
  above it showed `SQLState: 57P03` because PostgreSQL was still in crash recovery.
  The image and its `spring.datasource.url` were correct. This is recorded in
  `media-service.md` so the misleading message is not debugged twice.
- End-to-end image upload verified (2026-11-16): logged in as alice@used.local,
  uploaded a profile image through the gateway, received `imgUrl=/api/media/{id}`
  with `img=null` and `imgtype=null`, and retrieved the bytes through the gateway
  (200). The old inline bytes were cleared in the database. Media stores relative
  URLs, so the image resolves against whatever origin the browser used.
- The browser edge is now implemented: `gateway` (nginx) owns port 8080, the
  frontend is internal-only, and every relative `/api/**` and `/auth/user/**`
  call resolves to its owning service. `/api/internal/**` and `/actuator/**`
  return 404 at the edge. Media stores relative `/api/media/{id}` URLs, so image
  URLs follow whatever origin the browser used.
- Frontend templates already tolerated both image shapes; nothing in the page
  code changed for routing.
- Docker preparation is now explicitly approved ahead of Kafka: nine per-service
  multi-stage Java 17 Dockerfiles, local Compose networking, PostgreSQL, RabbitMQ,
  and optional pgAdmin are configured. See `docker-setup.md`. Compose validation
  and all nine image builds pass. The initial startup check exposed two defects:
  six HTTP-client services lacked a `RestClient.Builder` bean, and Media lacked
  datasource configuration. Both were fixed and the images rebuilt. Container
  database `used` now has the full schema (16 tables, all owned by `used`)
  plus seed data (20 users, 42 listings, 10 categories, etc.), bootstrapped from
  the host `used` database by `scripts/Bootstrap-Database.ps1`. The schema is
  RDS-compatible: no Docker-specific features, standard roles, UTF8. The old
  corrupted volume was abandoned rather than recovered.
- Kafka payment implementation compiles; 19 isolated tests and one live-broker
  test pass (production listener/retry/executor wiring, mocked payment work).
  The Kafka Docker service is healthy. The rebuilt Marketplace starts with health
  UP; production topics and three consumer assignments are verified. No actual
  payment was submitted in that startup check. Full wallet/database integration evidence
  is still pending; broker tests do not prove money-transfer correctness.
- Outbox remains pending. The gateway is implemented and verified
  (`scripts/Test-Gateway.ps1` -> `GATEWAY_SMOKE_OK`, 9/9). Full Docker
  business-workflow verification is no longer pending for images: a profile image
  upload was performed end-to-end through the gateway and the stored `imgUrl` was
  retrieved successfully. The gateway
  is implemented and verified (`scripts/Test-Gateway.ps1` → `GATEWAY_SMOKE_OK`,
  9/9). The browser pass (load `/`, confirm listings render, upload a profile
  image) is now possible with the bootstrapped database.
- Dev box fix applied: `JAVA_TOOL_OPTIONS=-Xmx384m -Xms128m` for every service
  (OOM was caused by each JVM defaulting to ~1.9 GiB heap on the 7.7 GiB WSL2
  VM) and `fsync=off`/`full_page_writes=off`/`synchronous_commit=off` on the dev
  Postgres (fsync was stalling 10–60 s per file). All nine services now start in
  ~3 minutes instead of 4–11, and the full stack runs without OOM. Both settings
  are dev-only; RDS keeps the defaults.
