# Project instructions

Before starting API, persistence, security, or messaging work, read
`docs/architecture-decisions.md`. It is the durable record of the agreed
microservice structure and must be carried into every session handoff.

Non-negotiables:

- Each service owns its tables; cross-service references are scalar IDs.
- Immediate calls use HTTP with `X-Internal-Token`; listing consistency uses
  RabbitMQ; payment work distribution uses Kafka (`payment-requests`, keyed by
  `listingId`, group `marketplace-payment-workers`).
- Do not split payment execution, wallet balances, the listing sale, or
  payment/transaction records out of Marketplace's local transaction.
- JWT stays in the HttpOnly `used_auth` cookie. CSRF uses `used_csrf` and
  `X-XSRF-TOKEN`. No Keycloak and no OAuth2 resource server.
- No schema changes except Media unless explicitly approved.
- No Docker or gateway until their dedicated phase.
- Report compile success, isolated tests, and simulation evidence separately.
  Compilation does not prove runtime integration.

When the moderation delist saga is implemented, follow the `DELISTING` +
idempotent-delist + RabbitMQ retry design in the architecture document.
