# API Documentation: Used Marketplace

This document outlines the RESTful endpoints available in the application.
Base URL: `http://localhost:8080`


## 🔐 Authentication (`/auth/user`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| Register User | `POST` | `/auth/user/register` | Creates a new user account. Sends verification email. | ❌ No |
| Verify Email | `POST` | `/auth/user/verify` | Validates the 6-digit code sent to user's email. Activates account. | ❌ No |
| Resend Code | `POST` | `/auth/user/resend-verify` | Triggers a new verification email (Rate-limited to 60s). | ❌ No |
| Login | `POST` | `/auth/user/login` | Authenticates user and returns a JWT Token. | ❌ No |
| Forget Password | `POST` | `/auth/user/forget-password` | Initiates password reset flow via email. | ❌ No |
| Reset Password | `POST` | `/auth/user/reset-password` | Sets new password using reset code. | ❌ No |
| Change Password | `PUT` | `/auth/user/change-password` | Updates password for logged-in user. | ✅ Yes |

## 📦 Listings (`/api/listings`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| Create Listing | `POST` | `/api/listings` | Adds a new item to the marketplace. | ✅ Yes |
| Get All Listings | `GET` | `/api/listings` | Returns listings across all statuses; use search for active listings. | ✅ Yes |
| Get Single Listing | `GET` | `/api/listings/{id}` | Fetches details for a specific item ID. | ❌ No |
| Update Listing | `PUT` | `/api/listings/{id}` | Edits title, price, description, etc. Owner only. | ✅ Yes |
| Delete Listing | `DELETE` | `/api/listings/{id}` | Delists an active listing. Owner only; admins use the separate endpoint. | ✅ Yes |
| My Listings | `GET` | `/api/listings/mine` | All of the current user's listings with status. SOLD rows carry `buyerUsername`, DELISTED rows carry `statusReason`. | ✅ Yes |
| Admin Delete | `DELETE` | `/api/listings/admin/{id}` | Force remove listing (Admin privilege). | ✅ Admin |
| Upload Image | `PUT` | `/api/listings/{id}/image` | Attaches/updates the main photo for a listing. | ✅ Yes |
| Buy Item | `POST` | `/api/listings/{id}/buy` | Executes a purchase transaction. Marks item as SOLD. | ✅ Yes |
| Search/Filter | `GET` | `/api/listings/search` | Unified endpoint for keyword search, category filtering, sorting, and pagination. Params: `keyword`, `categoryIds`, `sortBy`, `sortDir`, `page`, `size`. | ❌ No |

## 🏷️ Categories (`/api/categories`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| List Categories | `GET` | `/api/categories` | Returns all available tags/categories for filtering. | ❌ No |
| Get Category | `GET` | `/api/categories/{id}` | Fetches specific category details. | ❌ No |
| Create Category | `POST` | `/api/categories` | Adds a new global category tag. | ✅ Admin |
| Delete Category | `DELETE` | `/api/categories/{id}` | Removes a category tag. | ✅ Admin |

## 👤 Profile & Activity (`/api/profile`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| Get My Profile | `GET` | `/api/profile` | Fetches current user's profile data (Bio, Phone, Stats). | ✅ Yes |
| Update Profile | `PUT` | `/api/profile` | Saves changes to First Name, Last Name, Bio, Phone. | ✅ Yes |
| Upload Avatar | `PUT` | `/api/profile/image` | Updates the user's profile picture. | ✅ Yes |
| Get Activity Log | `GET` | `/api/profile/activity` | Returns chronological list of Audit Logs for the user. | ✅ Yes |

## 🔔 Real-Time Notifications (`/api/notifications`)

| Request | Method | Endpoint | Description | Auth Required? |
| :--- | :---: | :--- | :--- | :---: |
| Subscribe Stream | `GET` | `/api/notifications/stream` | Opens an SSE connection for real-time events. Requires `?token=JWT`. | ✅ Yes |
| History | `GET` | `/api/notifications?page=0&size=20` | Returns a list of persisted notifications for the current user. | ✅ Yes |
| Unread count | `GET` | `/api/notifications/unread-count` | Returns `{"count": 3}`. | ✅ Yes |
| Mark read | `PUT` | `/api/notifications/{id}/read` | Marks own notification read; 204. | ✅ Yes |
| Mark all read | `PUT` | `/api/notifications/read-all` | Marks own notifications read; 204. | ✅ Yes |

## Authentication and response conventions

Send `Authorization: Bearer <token>` on protected API calls. Login success returns
`{"message":"<JWT>","role":"USER"}` (or `ADMIN`). HTML routing is separate:
users go to `/`, admins to `/admin`. HTML page access does not grant API access.
Do not treat an HTML redirect as an API authorization check.

Business errors generally return `{"message":"..."}`: 400 for invalid actions,
404 for missing resources, 429 for rate limits. Authentication failures may be
401 or 403 with a different Spring error shape. Display a fallback message when
`message` is absent. Do not automatically retry purchases or report submissions.

## Favorites — authenticated

| Method | Endpoint | Success / behavior |
| --- | --- | --- |
| POST | `/api/favorites/{listingId}` | 200, FavoriteResponse; rejects own listing and duplicate favorite with 400 |
| DELETE | `/api/favorites/{listingId}` | 204; absent favorite is 404 |
| GET | `/api/favorites?page=0&size=12` | Page of FavoriteResponse, newest saved first |
| GET | `/api/favorites/{listingId}/check` | `{"favorited":true}` for current user |
| GET | `/api/favorites/{listingId}/count` | `{"count":2}` |

FavoriteResponse example:
```json
{"id":1,"listingId":34,"listingTitle":"Camera","listingPrice":100.0,"listingStatus":"ACTIVE","listingImgUrl":null,"createdAt":"2026-10-28T12:00:00"}
```
The page response contains `content`, `number` (zero-based), `size`, `totalElements`,
`totalPages`, `first`, and `last`. The `/favorites` HTML page uses this pagination.
Images stored only as binary listing uploads may have a null `listingImgUrl`.
Sale/delist flows remove existing favorites and notify their owners. Concurrent
favorite creation during sale is serialized on the listing row lock; a save
arriving while an item is being purchased is either rejected or removed by the
sale cleanup, never left behind.

## Reports — authenticated

| Method | Endpoint | Success / behavior |
| --- | --- | --- |
| POST | `/api/reports` | 200, newly created report |
| GET | `/api/reports/mine?page=0&size=10` | Current user's reports, paginated; excludes internal admin fields |
| GET | `/api/admin/reports?status=PENDING&page=0&size=20` | Admin queue; status optional |
| GET | `/api/admin/reports/{id}` | Admin detail with internal notes |
| GET | `/api/admin/reports/listing/{listingId}` | List of reports for listing |
| PUT | `/api/admin/reports/{id}/status` | No body; PENDING → UNDER_REVIEW |
| PUT | `/api/admin/reports/{id}/resolve` | Resolve and optionally delist |

Submit body:
```json
{"listingId":34,"reason":"SUSPECTED_SCAM","description":"Details to investigate"}
```
Reasons: `MISLEADING_LISTING`, `PROHIBITED_ITEM`, `SUSPECTED_SCAM`,
`INAPPROPRIATE_CONTENT`, `DUPLICATE_LISTING`, `INCORRECT_INFORMATION`, `OTHER`.
Description is optional, maximum 1000 characters. Own-listing and duplicate open
reports return 400. Five reports in a rolling 24-hour window triggers 429.
Sold/delisted listings may still be reported. A dismissed report permits re-reporting.

Resolve body:
```json
{"decision":"ACTION_TAKEN","adminNotes":"Reviewed by moderation","delistListing":true}
```
Decision is `DISMISSED` or `ACTION_TAKEN`; notes are optional, max 1000 characters.
Already resolved reports cannot be resolved again. Sold listings cannot be delisted;
the report can still resolve with an explanatory admin note. All `/api/admin/reports`
operations require ADMIN.

## Payments and wallet — authenticated

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/api/payments/{listingId}?method=WALLET` | 202; accepts asynchronous payment |
| GET | `/api/payments/{id}` | Payment status for polling |
| GET | `/api/payments/cod/pending` | Seller's pending COD requests |
| POST | `/api/payments/{id}/cod/accept` | Seller accepts queued COD request |
| POST | `/api/payments/{id}/cod/decline` | Seller declines COD request |
| GET | `/api/wallet` | Current wallet |
| POST | `/api/wallet/add` | Amount body, e.g. `{"amount":100.00}` |
| POST | `/api/wallet/remove` | Amount body; subject to available balance |

Payment acceptance example: `{"paymentId":42,"duplicate":false,"queuePosition":1}`.
**202 is not payment completion.** Poll for `COMPLETED` or `FAILED`. Methods are
`WALLET` (default) and `COD`; `CARD` currently returns 400. COD expires after five
minutes if unanswered. Queue saturation may return 429. A competing wallet purchase
may be rejected before a payment row is created, or fail asynchronously.
The legacy `/api/listings/{id}/buy` route is not the wallet-payment workflow.

## Administration and chat

ADMIN: `GET /api/admin/users`, `GET /api/admin/users/{id}`,
`PUT /api/admin/users/{id}/ban`, `PUT /api/admin/users/{id}/unban`,
`DELETE /api/admin/users/{id}`. Portal pages: `/admin`, `/admin/users`,
`/admin/listings`, `/admin/reports`.

Chat: `POST /api/chat/conversation?listingId={id}` creates/gets a conversation;
`GET /api/chat/conversations` lists the current user's conversations;
`GET /api/chat/conversation/{id}/messages` reads messages;
`POST /api/chat/conversation/{id}/messages` sends a message. These require authentication
and conversation membership. `DELETE /api/chat/conversation/{id}` removes the
conversation from the CURRENT user's chat list only (204) — the other participant
keeps theirs, and a new message makes the chat reappear for its recipient.

## SSE delivery

Notifications persist in the existing `notifications` table. Live delivery happens
after transaction commit, not on rollback. Named events are also delivered as
`NOTIFICATION`; clients should deduplicate by `notificationId`. SSE event IDs support
reconnection replay via `Last-Event-ID`. History remains the durable source of truth.
Saved-listing events include `LISTING_UPDATED` and `LISTING_UNAVAILABLE`; existing COD
events remain supported. Email delivery is best-effort after commit for favorite removal;
an SMTP failure is logged and does not reverse the completed transaction.
