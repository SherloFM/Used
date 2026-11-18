# EC2 JAR deployment and Jenkins

## Topology and prerequisites

This deployment uses one Amazon Linux 2023 x86_64 EC2 instance (currently
`m7i-flex.large`, 8 GiB). Nine Java 17 JAR processes run as the `used` system user.
PostgreSQL 18.6, RabbitMQ 4.4.0 and Kafka 4.1.0 run in Docker. nginx runs on the
host at port 80. Jenkins runs on Java 21 at port 8090, with Java 17 for builds.
All backend ports and infrastructure bindings are loopback-only.

This is a single-machine demonstration deployment, with restart downtime and
shared memory/CPU between builds and serving traffic. One Jenkins executor,
sequential Maven modules, bounded JVM heaps, and five JDBC connections per
service reduce contention; they do not guarantee capacity under load.

Use `deploy/ec2/compose.yaml`, not the development root Compose file. EC2 keeps
PostgreSQL durability enabled. No database reset or migration runs in Jenkins.
Payment transactions remain entirely within Marketplace; RabbitMQ and Kafka
retain the contracts in `architecture-decisions.md`.

### Required moderation constraint update

Deployments initialized before the `DELISTING` moderation state was added have a
`listing_reports_status_check` constraint that rejects the saga's first state
change. Apply this one-time, transactional patch before deploying the matching
service release; it preserves all rows and only adds the missing allowed value:

```bash
cd ~/used && git pull # end
```

```bash
sudo /usr/local/bin/docker-compose --env-file /etc/used/used.env -f /etc/used/compose.yaml exec -T postgres psql -v ON_ERROR_STOP=1 -U used -d used < services/moderation-service/src/main/resources/db/allow-delisting-status.sql # end
```

Verify the constraint before retrying the admin action:

```bash
sudo /usr/local/bin/docker-compose --env-file /etc/used/used.env -f /etc/used/compose.yaml exec -T postgres psql -U used -d used -c "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'listing_reports_status_check';" # end
```

## 1. AWS console

1. Keep using the existing instance in Europe (Stockholm). Confirm 8 GiB RAM and
   Docker using the checks already performed. A new EC2 instance is unnecessary.
2. Billing → Credits: confirm EC2 eligibility, remaining balance and expiration.
   Available instance types alone do not establish credit eligibility.
3. EC2 → instance → Storage: allow at least 30 GiB, preferably 50 GiB gp3 for
   sources, Maven cache, JAR releases, Jenkins artifacts, logs and database data.
   If expanding an existing EBS volume, the filesystem must also be expanded;
   inspect `lsblk` and `df -h /` before choosing the device/partition commands.
4. Security → security group → Edit inbound rules:
   - HTTP TCP 80 for the intended audience (public website: `0.0.0.0/0`).
   - Custom TCP 8090 **My IP only** for the Jenkins setup UI.
   - Preserve the working EC2 Instance Connect SSH rule. Browser-based Instance
     Connect may originate from the AWS regional service range, not your IP.
   - No public 5432, 5672, 9092, 15672, or 8080–8088 rules are needed.
   - Add 443 when HTTPS is configured.
5. Connect using EC2 Instance Connect. The `# end` suffix on single commands
   protects against the duplicate-paste issue seen in this browser terminal.

## 2. Publish the checkout and get it onto EC2

The local working tree contains the service migration and deployment files that
must be reviewed, committed and pushed before a GitHub clone can contain them.
Do not commit `.env`, private keys, `target/`, or database data. `.gitignore`
excludes PEM/key files. A Git remote existing does not prove the new files were
pushed. The currently checked-out branch is `master`.

On EC2, run each command separately:

```bash
sudo dnf install -y git # end
```

```bash
git clone https://github.com/SherloFM/Used.git ~/used # end
```

```bash
cd ~/used # end
```

```bash
ls deploy/ec2 Jenkinsfile # end
```

For a private repository use a read-only GitHub deploy key or a fine-grained
read-only token; enter credentials through prompts/credential stores, never
embed a token into the clone URL or commit it. Jenkins needs its own SCM
credential later. If the checkout already exists, inspect its status before
pulling; do not overwrite local changes.

## 3. Install host services

Preview `deploy/ec2/install-host.sh`, then run:

```bash
sudo bash deploy/ec2/install-host.sh # end
```

The script installs Corretto 17/21, nginx and Jenkins LTS, reuses the existing
`/usr/local/bin/docker-compose`, installs root-owned service definitions and
creates `/etc/used/used.env` with mode 0600. It does not start the app or import
data. It uses the official Jenkins RPM repository and 2026 signing key, based
on https://www.jenkins.io/doc/book/installing/linux/ .

nginx's full configuration is generated from `gateway/nginx.conf` into
`/etc/nginx/used.conf`, replacing Docker names with loopback IPs and listening
on port 80. It preserves route ownership, blocked internal paths, SSE and
WebSocket settings. Root-owned systemd overrides select this configuration.

Jenkins has permission to restart/stop only the nine application systemd units;
it is not added to the Docker group. Repository code runs as `jenkins`, so only
trusted maintainers should be allowed to modify the deployment branch or job.

## 4. Configure runtime secrets on EC2

Edit `sudo nano /etc/used/used.env` (install nano if needed). Fill every
`REPLACE_` placeholder. Generate values locally on the EC2 terminal:

```bash
openssl rand -hex 32 # end
```

Run separately for DB password, RabbitMQ password, and internal token. For JWT:

```bash
openssl rand -base64 48 # end
```

For the stable Kafka cluster ID:

```bash
sudo docker run --rm apache/kafka:4.1.0 /opt/kafka/bin/kafka-storage.sh random-uuid # end
```

Use simple unquoted values in the environment file. Set `FRONTEND_ORIGINS` to
`http://PUBLIC_IP` for the temporary HTTP demo. SMTP settings must point to a
working mail provider, including username/password, auth and STARTTLS settings
appropriate to it. Notification's aggregate health includes SMTP; fake SMTP
settings intentionally fail the deployment gate. Never paste secrets into chat.

`AUTH_COOKIE_SECURE=false` supports the temporary HTTP demonstration. Before
using real accounts on the public site, configure HTTPS, set it to `true`, and
set the origin to `https://DOMAIN`. TLS is a separate user-owned setup phase.

Infrastructure credentials initialize persistent volumes only on first startup;
editing the env file later does not rotate existing database/broker passwords.

### Almotawaj Wallet gateway

Four optional keys enable paying on the Almotawaj checkout page. `configure-env.sh`
only writes them for a fresh file, so an environment file created by an earlier
install keeps working unchanged: with no API key the method stays visible but
reports that wallet payments are not available yet.

```text
WALLET_GATEWAY_BASE_URL=https://wallet.almotawaj.com
WALLET_GATEWAY_API_KEY=<from the Almotawaj API keys page>
WALLET_GATEWAY_SIGNING_SECRET=<the same page's callback signing secret>
WALLET_GATEWAY_PUBLIC_BASE_URL=https://used.thrifty.bid
```

Add them to `/etc/used/used.env` with an editor, then restart only Marketplace:

```bash
sudo systemctl restart used@marketplace-service # end
```

On the Almotawaj API keys page, set the callback URL to
`https://used.thrifty.bid/api/payments/gateway/callback`. The gateway signs each
callback with the signing secret; without that secret Marketplace rejects every
callback with 401 rather than trusting an unsigned body. `WALLET_GATEWAY_PUBLIC_BASE_URL`
must be the HTTPS origin, otherwise the return page loses the `Secure` auth cookie
and the buyer appears logged out.

Gateway payments are settlement records, not a wallet top-up: the buyer pays on
the gateway, and only the seller's Marketplace balance is credited when the sale
completes. Prices are BHD throughout and are sent to the gateway unchanged, so a
listing below 0.100 or above 5000.000 cannot be paid through the gateway and the
checkout says so instead of creating a session.

## 5. Start infrastructure and initialize the empty database

```bash
sudo bash deploy/ec2/start-infra.sh # end
```

This waits for health and creates both `payment-requests` and
`payment-requests.DLT` with three partitions. The stable cluster ID and all
three named volumes must survive application updates.

```bash
sudo bash deploy/ec2/init-database.sh # end
```

`schema.sql` is a schema-only export of the working local PostgreSQL database,
including existing supplemental DDL. It creates the same 16 tables and existing
constraints/indexes, including legacy foreign keys; this does not redesign the
schema. The importer refuses a nonempty database and applies the schema in one
transaction. It contains no personal data, passwords, demo users or listings.
Use normal registration/verification for initial accounts. Demo/admin provisioning
and category data require a separate explicit choice; local demo credentials do
not automatically exist on EC2.

## 6. Complete Jenkins setup

1. Browse to `http://PUBLIC_IP:8090` from the IP allowed by the security group.
2. In the EC2 terminal run `sudo cat /var/lib/jenkins/secrets/initialAdminPassword`.
   Enter it only in Jenkins. Install suggested plugins and create an admin user.
3. Ensure Pipeline, Git, Docker, Docker Pipeline, JUnit and Timestamper plugins
   are installed.
4. In the EC2 terminal run `sudo usermod -aG docker jenkins` and then
   `sudo systemctl restart jenkins`. The isolated PostgreSQL stage starts a
   throwaway Testcontainers database through the Docker socket. The installer
   applies this group change on a fresh host; these two commands update an
   already-installed host.
5. Manage Jenkins → Nodes → Built-In Node → Configure:
   - Number of executors: **1**.
   - Labels: **used-ec2**.
   - Usage: only build jobs with label expressions matching this node.
   Running this trusted job on the controller is a resource-limited demo tradeoff;
   use a separate agent for a larger/shared installation.
6. For a private repo, Manage Jenkins → Credentials: add read-only GitHub SCM
   credentials (HTTPS token as username/password, or SSH key with SSH repo URL).
7. New Item → `used-marketplace` → **Pipeline**.
8. Definition: **Pipeline script from SCM**; SCM: Git.
9. Repository: `https://github.com/SherloFM/Used.git`; select credentials if needed.
10. Branch specifier: **`*/master`**; Script path: **`Jenkinsfile`**.
    This is a single trusted-branch job, not a pull-request/multibranch job.
11. Save → **Build Now**. The first run installs the polling trigger. Thereafter
    Jenkins polls GitHub every five minutes and builds only when SCM changes.
    No publicly accessible GitHub webhook is required.

The pipeline checks out a clean workspace, builds sequentially, runs the existing
per-service unit/Cucumber MockMvc tests, rejects tracked secrets, runs the
dedicated PostgreSQL concurrency tests in their own Docker-backed stage, publishes
JUnit results, archives all nine JARs, switches the release symlink and restarts
each service independently. Java tests run without runtime production secrets.
Selenium `e2e-tests` is explicitly excluded: its runner has not yet been proven
to execute.

The deployment checks all nine aggregate health endpoints and the gateway,
including blocked internal routes. Failure restores and checks the previous
JAR release; on first-deployment failure it stops all app units. Database data
is untouched. A deployment is not zero-downtime, and rollback cannot undo data
writes made while the failed version was running. SMTP failure also triggers
rollback. Review `/opt/used/health-last.log` and service journals for diagnosis.

## 7. Monitoring and CloudWatch

`install-host.sh` installs `used-monitor.timer`. Every minute it checks all nine
service health endpoints and the gateway, measures root-disk and memory usage,
and writes one structured line to journald:

```bash
systemctl status used-monitor.timer --no-pager # end
```

```bash
journalctl -u used-monitor.service -n 20 --no-pager # end
```

This local monitoring works without AWS permissions. To publish the same values
to CloudWatch, attach an EC2 instance role with this minimum policy (scope it
further through your AWS account controls where appropriate):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "cloudwatch:PutMetricData",
    "Resource": "*",
    "Condition": {"StringEquals": {"cloudwatch:namespace": "UsedMarketplace"}}
  }]
}
```

Confirm AWS CLI v2 is available, then edit `/etc/used/monitoring.env`:

```text
CLOUDWATCH_ENABLED=true
CLOUDWATCH_NAMESPACE=UsedMarketplace
AWS_REGION=eu-north-1
```

Run one check and inspect the result:

```bash
sudo systemctl start used-monitor.service && sudo systemctl status used-monitor.service --no-pager # end
```

The custom metrics are `HealthyServices`, `GatewayHealthy`, `DiskUsedPercent`,
and `MemoryUsedPercent`, dimensioned by EC2 instance ID. To create alarms, first
add `cloudwatch:PutMetricAlarm` to the setup operator (not necessarily the EC2
runtime role), then run from the checkout:

```bash
AWS_REGION=eu-north-1 sudo -E bash deploy/ec2/create-cloudwatch-alarms.sh # end
```

Optionally set `SNS_TOPIC_ARN` to a confirmed SNS topic before running the script
to attach alarm and recovery notifications. Creating an alarm without an action
records state in CloudWatch but sends no email.

This is basic host/application monitoring, not distributed tracing. RabbitMQ
queue depth, Kafka consumer lag, JVM metrics, and centralized log search remain
future improvements.

## 8. HTTPS

The site is served over plain HTTP until a certificate exists. A browser on a
network that blocks outbound TCP 80 needs HTTPS, so this step is what makes the
site usable for the owner.

Prerequisites, all outside the instance:

1. **Elastic IP.** EC2 → Elastic IPs → allocate (if none) and associate it with
   the instance. A dynamic public IPv4 changes on stop/start and breaks the DNS
   record. Record the address; the domain must point at it.
2. **Domain.** Cloudflare Registrar, or any registrar with DNS control.
3. **DNS record.** Cloudflare → DNS → Records → Add record: type **A**, name
   `used` (or `@` for the apex), IPv4 **the Elastic IP**, proxy status
   **DNS only** (grey cloud) for the first issuance. An orange-cloud record
   usually still works because the challenge is also served on 443, but the grey
   cloud removes a variable while debugging.
4. **Security group.** Inbound 80 and 443 from `0.0.0.0/0`. Port 80 stays open
   permanently: renewal uses it.

Confirm the record resolves to the Elastic IP **before** requesting a
certificate; a wrong A record is the most common failure.

```bash
nslookup used.example.com # end
```

Validate the generated nginx configuration without changing the running site
(the generator writes to `/tmp`, `nginx -t` only parses it):

```bash
cd ~/used && git pull # end
```

```bash
sudo python3 deploy/ec2/render-nginx.py gateway/nginx.conf /tmp/used-nginx-test.conf && sudo nginx -t -c /tmp/used-nginx-test.conf # end
```

Then issue the certificate and switch the site to HTTPS:

```bash
sudo bash deploy/ec2/enable-https.sh used.example.com you@example.com # end
```

The script stops at the first failure and leaves plain HTTP working. It:

1. records the hostname in `/etc/used/https.env` (no secrets, mode 0644),
2. installs certbot (distribution package, else `/opt/certbot` virtualenv),
3. serves `/.well-known/acme-challenge/` from `/var/www/certbot` and reloads,
4. issues the certificate with the webroot method,
5. re-renders nginx with a 443 listener, HTTP→HTTPS redirect for the real
   hostname, and HSTS,
6. sets `AUTH_COOKIE_SECURE=true`, `FRONTEND_ORIGINS=https://DOMAIN` and
   `WALLET_GATEWAY_PUBLIC_BASE_URL=https://DOMAIN`, then restarts the nine
   services so the cookies stop travelling over plain HTTP,
7. installs the `used-certbot-renew.timer` daily renewal (nginx reload hook),
8. waits for the health gate, which now validates the real certificate on
   loopback through `--resolve`.

Plain HTTP still answers on `127.0.0.1` and on the bare address, so the health
gate and `curl` diagnostics keep working; only requests carrying the real
hostname are redirected. `render-nginx.py` keeps the TLS listener whenever
`/etc/used/https.env` and the certificate exist, so re-running `install-host.sh`
does not downgrade the site.

Verify:

```bash
curl -I https://used.example.com # end
```

```bash
systemctl list-timers used-certbot-renew.timer --no-pager # end
```

Then load the site in a browser, log in, and confirm the `used_auth` cookie is
`Secure` in devtools. Renewal is only proven after the first renewal window;
`certbot renew --dry-run` is the explicit check.

## 9. Verify, then enable application boot startup

After the first successful Jenkins deployment, open `http://PUBLIC_IP` and test
registration, login, listing/image creation and a payment with demo funds.
Health checks alone do not prove these business workflows.

Enable startup only after that first successful deployment:

```bash
sudo systemctl enable used@accounts-service used@chat-service used@marketplace-service used@favorites-service used@notification-service used@moderation-service used@audit-service used@media-service used@frontend-service # end
```

Docker infrastructure uses `restart: unless-stopped`. Java services retry on
failure while the database/brokers recover at boot. Jenkins and nginx are already
enabled by the installer.

Logs:

```bash
sudo journalctl -u used@marketplace-service -n 100 --no-pager # end
```

```bash
sudo journalctl -u jenkins -n 100 --no-pager # end
```

Keep previous releases until the latest deployment is verified. Jenkins retains
five build histories and two artifact sets; `/opt/used/releases` is separate and
needs periodic reviewed cleanup. Take database backups and EBS snapshots before
later schema changes. Never use `docker compose down -v` as a redeployment step.

## Evidence boundaries

Local preparation (2026-11-17): Bash syntax and Compose configuration validation
passed; the EC2 private key is ignored by Git. A schema-only export completed.
The isolated PostgreSQL restore and containerized deployment orchestration checks
both timed out without results; they are **not passing evidence**. The restore
attempt may have left a temporary database named `used_schema_verify_<8 hex>` in
the local Docker PostgreSQL instance; inspect and remove that test database once
the local Docker commands respond. It does not target the `used` database.
For the 2026-11-19 documentation/CI changes, local Git whitespace validation,
Bash syntax validation, the tracked-secret check, and Marketplace test compilation
passed. The PostgreSQL stage and its Testcontainers runtime could not be executed
locally because the local Docker daemon returned HTTP 500. No AWS deployment,
Jenkins run, or live workflow verification is part of this local change.

Run the deployment orchestration checks on Linux with
`bash deploy/ec2/tests/deployment-checks.sh`. They use temporary release trees
and stand-ins for systemctl and health checks to cover success, rollback,
rollback failure, first-release failure and missing artifacts. They do not
exercise real Spring processes or AWS.

- Jenkins pipeline execution and AWS runtime verification require the user's EC2
  setup. A local file validation is not an observed Jenkins run.
- Existing Java/MockMvc test success is isolated evidence, not browser coverage.
- Startup/health checks do not prove payment atomicity, authenticated SSE, or
  complete end-to-end workflows. No new upstream simulation is part of this
  deployment-only change.
