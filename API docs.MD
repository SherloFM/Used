# Used Marketplace API Reference

## Origins and routing

| Environment | Base URL |
|---|---|
| Production | `https://used.thrifty.bid` |
| Local gateway | `http://localhost:8080` |

All browser requests use the NGINX gateway. `/api/internal/**` and
`/actuator/**` are deliberately unavailable at the public edge.

## Authentication and CSRF

Login and successful email verification issue a signed JWT in the HttpOnly
`used_auth` cookie. JavaScript never reads or stores the token. Protected calls
must include cookies:

```javascript
fetch('/api/profile', { credentials: 'include' })
```

Browser state-changing requests also require CSRF protection:

1. `GET /auth/user/csrf` returns `{ "headerName": "X-XSRF-TOKEN", "token": "..." }`.
2. Send that token in `X-XSRF-TOKEN` with POST, PUT, PATCH, and DELETE requests.
3. Continue sending `credentials: "include"`.

The frontend's `authenticatedFetch` helper performs this exchange. The public
Almotawaj callback is CSRF-exempt because it authenticates with
`X-Wallet-Signature`, not a browser session.

Common responses:

| Status | Meaning |
|---:|---|
| 200/201/202/204 | Successful, created, accepted for asynchronous work, or no body |
| 400 | Invalid input or business rule rejection |
| 401 | Authentication or callback signature missing/invalid |
| 403 | Wrong role or invalid CSRF token |
| 404 | Resource not found or intentionally hidden |
| 409 | Conflicting state where explicitly returned |
| 413 | Image exceeds the multipart limit |
| 429 | Rate limit exceeded |

Business errors generally use `{ "message": "..." }`.

## Accounts and sessions

Base path: `/auth/user`

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| POST | `/register` | Public | Creates an unverified account and sends a six-digit code |
| POST | `/verify` | Public | Verifies email, issues cookies, and signs the member in |
| POST | `/resend-verify` | Public | Resends after the configured cooldown |
| POST | `/login` | Public | Issues `used_auth`; response contains message and role, never the token |
| GET | `/csrf` | Public | Creates/returns the CSRF token used by browser writes |
| GET | `/session` | Member | Returns `{id, username, role}` with `Cache-Control: no-store` |
| POST | `/logout` | Member | Clears authentication and CSRF state; returns 204 |
| POST | `/forget-password` | Public | Sends a password-reset code |
| POST | `/reset-password` | Public | Resets with a valid code |
| PUT | `/change-password` | Member | Changes the current member's password |

## Profile and activity

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/profile` | Member | Current profile |
| PUT | `/api/profile` | Member | Update name, phone, and biography |
| PUT | `/api/profile/image` | Member | Multipart field `img`; updates profile image |
| GET | `/api/profile/activity` | Member | Current member's audit timeline |

Phone images are oriented, resized, and converted to JPEG in the browser before
upload. Accounts enforces a 5 MB file limit and forwards validated ownership to
Media.

## Listings

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/listings` | Public | Listings across statuses |
| GET | `/api/listings/{id}` | Public | Listing detail |
| GET | `/api/listings/search` | Public | Active listings with pagination and filters |
| GET | `/api/listings/mine` | Member | Current seller's listings across statuses |
| POST | `/api/listings` | Member | Create a listing |
| PUT | `/api/listings/{id}` | Owner | Update an active listing |
| PUT | `/api/listings/{id}/image` | Owner | Multipart field `img`; add or replace image |
| DELETE | `/api/listings/{id}` | Owner | Idempotent business delist for the owner |
| DELETE | `/api/listings/admin/{id}` | Admin | Administrator delist |
| POST | `/api/listings/{id}/buy` | Member | Legacy synchronous purchase path |

Search parameters:

| Parameter | Default | Meaning |
|---|---|---|
| `keyword` | empty | Case-insensitive title search |
| `categoryIds` | empty | Repeated category IDs; listing must match all selected IDs |
| `sortBy` | service default | Allowed listing sort field |
| `sortDir` | `asc` | `asc` or `desc` |
| `page` | `0` | Zero-based page |
| `size` | `12` | Page size |

Create/update bodies use listing fields such as `title`, `description`, `price`,
`condition`, `location`, and `categories: [{"id": 1}]`. Seller identity is taken
from the cookie, never trusted from the body.

## Categories

| Method | Endpoint | Access |
|---|---|---|
| GET | `/api/categories` | Public |
| GET | `/api/categories/{id}` | Public |
| POST | `/api/categories` | Admin |
| DELETE | `/api/categories/{id}` | Admin |

## Favorites

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| POST | `/api/favorites/{listingId}` | Member | Save an active listing; rejects own/duplicate save |
| DELETE | `/api/favorites/{listingId}` | Member | Remove own favorite |
| GET | `/api/favorites?page=0&size=12` | Member | Paginated favorites |
| GET | `/api/favorites/{listingId}/check` | Member | `{ "favorited": true }` |
| GET | `/api/favorites/{listingId}/count` | Member | `{ "count": 2 }` |

RabbitMQ `listing.updated`, `listing.sold`, and `listing.delisted` events reconcile
saved snapshots. Sold/delisted favorites are removed asynchronously.

## Reports and moderation

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| POST | `/api/reports` | Member | Submit a listing report |
| GET | `/api/reports/mine?page=0&size=10` | Member | Own reports without internal notes |
| GET | `/api/admin/reports` | Admin | Paginated queue; optional `status` |
| GET | `/api/admin/reports/{id}` | Admin | Report detail |
| GET | `/api/admin/reports/listing/{listingId}` | Admin | Report history for a listing |
| PUT | `/api/admin/reports/{id}/status` | Admin | `PENDING → UNDER_REVIEW` |
| PUT | `/api/admin/reports/{id}/resolve` | Admin | Dismiss, resolve, or start delisting |

Submit example:

```json
{
  "listingId": 34,
  "reason": "SUSPECTED_SCAM",
  "description": "The seller requests payment outside the platform."
}
```

Resolve and delist:

```json
{
  "decision": "ACTION_TAKEN",
  "adminNotes": "Confirmed policy violation.",
  "delistListing": true
}
```

With `delistListing: true`, the report first commits as `DELISTING`. RabbitMQ
sends an idempotent command to Marketplace. The result changes the report to
`ACTION_TAKEN`; a transient failure remains `DELISTING` for the retry scheduler.

## Payments

### Internal wallet and COD

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| POST | `/api/payments/{listingId}?method=WALLET` | Member | Returns 202 after recording payment work |
| POST | `/api/payments/{listingId}?method=COD` | Member | Queues a COD request |
| GET | `/api/payments/{id}` | Participant | Poll payment state |
| GET | `/api/payments/cod/pending` | Seller | Pending requests for owned listings |
| POST | `/api/payments/{id}/cod/accept` | Seller | Accept COD request |
| POST | `/api/payments/{id}/cod/decline` | Seller | Decline COD request |

`202 Accepted` is not completion. Wallet requests are distributed through Kafka
topic `payment-requests`, keyed by `listingId`. Poll until `COMPLETED` or `FAILED`.
Retries and recovery can redeliver work; execution is idempotent.

### Almotawaj Wallet

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| POST | `/api/payments/{listingId}/gateway` | Member | Creates a checkout session; returns 201 and `checkoutUrl` |
| GET | `/api/payments/{id}/gateway?sessionId=...` | Buyer | Verifies returned session and completes paid order |
| POST | `/api/payments/gateway/callback` | Signed gateway | HMAC-authenticated server callback |

The external gateway is represented by payment method `CARD` internally, but no
card details are collected by Used Marketplace. Amounts are BHD. Completion
credits the seller without debiting the buyer's internal wallet because the buyer
already paid externally.

## Wallet

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/wallet` | Member | Current member's balance |
| POST | `/api/wallet/admin/{userId}/fund` | Admin | Atomic credit to selected member |

Funding body:

```json
{ "amount": 25.00 }
```

Members cannot modify their own balances. Payment debits and seller credits are
performed only by Marketplace's transaction logic.

## Notifications

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| GET | `/api/notifications/stream` | Member cookie | SSE stream; no query-string JWT |
| GET | `/api/notifications?page=0&size=20` | Member | Persisted history |
| GET | `/api/notifications/unread-count` | Member | Unread count |
| PUT | `/api/notifications/{id}/read` | Owner | Mark one read; 204 |
| PUT | `/api/notifications/read-all` | Member | Mark all read; 204 |

SSE uses the same-origin `used_auth` cookie. Event IDs support reconnection; the
history endpoint remains the durable source of truth.

## Chat

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| POST | `/api/chat/conversation?listingId={id}` | Member | Create/get conversation |
| GET | `/api/chat/conversations` | Member | Visible conversation list |
| GET | `/api/chat/conversation/{id}/messages` | Participant | Message history |
| POST | `/api/chat/conversation/{id}/messages` | Participant | Send message |
| DELETE | `/api/chat/conversation/{id}` | Participant | Hide from current member only |

WebSocket/STOMP connects through `/ws` using the same authenticated origin.

## Administration

| Method | Endpoint | Access |
|---|---|---|
| GET | `/api/admin/users` | Admin |
| GET | `/api/admin/users/{id}` | Admin |
| PUT | `/api/admin/users/{id}/ban` | Admin |
| PUT | `/api/admin/users/{id}/unban` | Admin |
| DELETE | `/api/admin/users/{id}` | Admin |

Admin HTML routes are `/admin`, `/admin/users`, `/admin/listings`, and
`/admin/reports`. UI visibility is not the authorization boundary; every API
endpoint enforces the role server-side.

## Media service

Browser callers upload through Accounts or Marketplace. Internal callers use:

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| POST | `/api/internal/media` | `X-Internal-Token` | Multipart `ownerId` and `file`; returns 201 |
| GET | `/api/media/{uuid}` | Public | Immutable JPEG/PNG with ETag and long cache |

Media validates bytes rather than trusting the extension. Maximum stored size is
5 MB; maximum dimensions are 8000×8000 and 40 megapixels. Stored paths are
relative (`/api/media/{uuid}`) so they work on local and production origins.

## Internal service endpoints

These are not routed through NGINX and require `X-Internal-Token`:

- Accounts: `GET /api/internal/users/{id}`
- Marketplace: `GET /api/internal/listings/{id}` and
  `POST /api/internal/listings/{id}/delist`
- Media: `POST /api/internal/media`
- Notification: internal push, broadcast, and email commands
- Audit: internal log and activity operations

See [architecture decisions](docs/architecture-decisions.md) for timeout,
messaging, consistency, and security guarantees.
