# Used Marketplace — Planning and Delivery Record

Last updated: 19 November 2026

## Product goal

Deliver a trustworthy local second-hand marketplace where verified members can
discover, list, discuss, save, report, and purchase goods, while administrators
can moderate content and manage platform risk.

## Success criteria

- A new member can register, verify their email, and enter the application.
- A seller can publish a realistic listing with a mobile photo and categories.
- A buyer can discover and purchase an available listing without duplicate sale
  or wallet overdraft.
- Listing lifecycle changes reach Favorites, Notification, and Moderation.
- Administrators can manage users, fund wallets, and resolve reports.
- The production application is HTTPS-accessible and recoverable through CI/CD.

## Delivered milestones

| Milestone | Status | Evidence |
|---|---|---|
| Core marketplace CRUD and authentication | Done | Accounts/Marketplace services and Cucumber scenarios |
| Microservice separation | Done | Nine Spring Boot services with owned tables |
| Single browser gateway | Done | NGINX route map and blocked internal endpoints |
| Media ownership | Done | Media service, mobile preparation, immutable URLs |
| Listing consistency | Done | RabbitMQ lifecycle events and moderation saga |
| Payment concurrency | Done | Atomic claims/debits and PostgreSQL CI tests |
| Kafka payment distribution | Done | `payment-requests`, retries, DLT, recovery scan |
| Payment methods | Done | Internal wallet, COD, Almotawaj Wallet |
| Administration | Done | Users, listings, reports, wallet funding |
| AWS deployment | Done | EC2, HTTPS, Jenkins, systemd, rollback |
| Presentation diagrams | Done | ERD and simplified/detailed architecture |
| Documentation refresh | Done | README, API reference, planning, credits |

## Current release priorities

### P0 — presentation and production confidence

- [ ] Apply the approved `DELISTING` constraint patch to production.
- [ ] Confirm Jenkins deployed the latest trusted `master` commit.
- [ ] Run the buyer-wallet purchase journey against production.
- [ ] Verify seller credit, sold listing, notification, and favorite cleanup.
- [ ] Test listing and profile photo upload from one iPhone and one Android device.
- [ ] Remove/delist presentation listings with placeholder titles or missing photos.
- [ ] Confirm buyer, seller, and administrator demonstration accounts.

### P1 — security and operations

- [ ] Revoke and replace the exposed Gmail App Password.
- [ ] Rotate any database/JWT value that reused a committed development value.
- [ ] Attach the EC2 monitoring IAM policy and enable CloudWatch metrics.
- [ ] Create CloudWatch alarms and connect an SNS email subscription.
- [ ] Confirm `certbot renew --dry-run` succeeds.
- [ ] Store a PostgreSQL backup outside the EC2 instance.

### P2 — engineering improvements

- [ ] Prove and enable Selenium E2E in CI.
- [ ] Add a transactional outbox for durable DB-to-broker publishing after schema approval.
- [ ] Move Media bytes from PostgreSQL to S3 while retaining the current API.
- [ ] Move PostgreSQL to RDS if the project becomes more than a demonstration.
- [ ] Add centralized structured-log search and broker lag/queue alarms.
- [ ] Remove the remaining root monolith code after final archival verification.

## Demo plan

1. Show the problem statement and live homepage.
2. Explain the ERD using users, listings/categories, wallets/payments/transactions,
   reports, favorites, and notifications.
3. Explain the architecture using NGINX, service ownership, RabbitMQ, Kafka, and SMTP.
4. Demonstrate a buyer finding and purchasing one realistic listing.
5. Show the listing becoming sold and the seller wallet increasing.
6. Optionally show an administrator funding a wallet or delisting a reported listing.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Single EC2 failure | Health checks, persistent volumes, release rollback, external backup |
| Duplicate payment delivery | Idempotent state transitions and conditional database updates |
| Broker temporarily unavailable | Kafka recovery/DLT and RabbitMQ retry/idempotent consumers |
| Lost DB-to-broker publish | Documented outbox limitation; retry where a durable state exists |
| External wallet unavailable | Internal wallet is the deterministic demonstration path |
| Mobile images too large/HEIC | Browser resize/conversion plus aligned multipart limits |
| Secret exposure | Environment files, tracked-secret CI check, documented rotation |

## Definition of done

A change is complete only when:

1. the owning service compiles;
2. relevant isolated tests pass;
3. database or broker behavior is tested with the real technology where required;
4. API/security/documentation changes are updated in the same commit;
5. deployment-specific schema steps are recorded and applied deliberately;
6. production behavior is verified separately from compilation and unit tests.
