# Accounts authentication contract (Part 7A)

JWTs are HS256-signed with `sub` = immutable user ID, `iss`, `aud`, `iat`,
`exp`, `roles`, and `username`. The username claim is display data only;
authorization decisions use `sub` and `roles`. JWT_SECRET must be Base64
encoding of at least 32 random bytes, configured outside source control.
Previous email-subject tokens are incompatible; sign in again. Signature
verification allows 30 seconds of clock skew. Accounts checks current account
existence, verification and ban state on each authenticated request. Other
services validate the signature and claims only, so a ban takes effect there
at the next token expiry (15 minutes).

## Browser flow

1. GET `/auth/user/csrf` with credentials included. Receives a shared HttpOnly
   `used_csrf` cookie and JSON `{headerName, token}`. Keep this
   **CSRF token**, not a JWT, in memory.
2. POST `/auth/user/login` with JSON `{email, password}`, cookies included and
   the CSRF token in `X-XSRF-TOKEN`. Login and all other unsafe methods require
   CSRF protection, including registration and logout.
3. Success: 200 `{message: "Signed in", role: "USER" | "ADMIN"}` and an
   HttpOnly, SameSite=Lax, Path=/, host-only `used_auth` cookie. Never a JWT in
   JSON. Cookie defaults to Secure and a lifetime of 15 minutes.
4. Fetch a fresh CSRF token after login (the previous cookie is cleared).
5. GET `/auth/user/session` returns `{id, username, role}` or 401. No JWT.
6. POST `/auth/user/logout` with CSRF clears both cookies and returns 204.
   Fetch a fresh CSRF token before signing in again. Logout clears browser
   credentials; it does not revoke a copied JWT before its expiry.

An invalid authentication cookie is cleared. Protected requests are rejected;
public login/CSRF/logout remain accessible so an expired cookie cannot trap the
user. JWT query parameters and Authorization bearer headers are not accepted by
the Accounts browser filter. No authentication token is logged.

## Service-to-service flow

Other services read user data only through `/api/internal/**`, authenticated
with a shared `INTERNAL_API_TOKEN` sent as `X-Internal-Token`. The token must be
at least 32 characters, has no default, and is configured outside source
control. These routes require `ROLE_INTERNAL`, which only that header grants, so
a valid browser `used_auth` cookie alone receives 401. A missing or wrong token
is rejected before the request reaches a controller.

`GET /api/internal/users/{id}` returns `{id, username, email, role}` or 404.
Profiles, passwords, verification codes and reset codes are never exposed.
No batch endpoint exists yet; callers that need several users issue one request
per id.

Missing/invalid CSRF yields 403. Access without authentication yields 401 on
protected GETs. Valid ordinary users cannot access `/api/admin/**`. Account
login errors retain their existing 401/403/429 responses.

For local HTTP set AUTH_COOKIE_SECURE=false. Configure FRONTEND_ORIGINS to an
explicit comma-separated list of allowed origins; credentialed wildcard CORS
is not supported. Cookies are not isolated by port. Different hostnames require
an explicit deployment/cookie-scope design. The CSRF cookie and header names
(`used_csrf` / `X-XSRF-TOKEN`) are shared by services on the same host.

Part 7A updates Accounts. Part 7B migrates the frontend to cookie auth. Part 7C
adds local cookie validation to Marketplace. Part 7D-1 adds the internal user
lookup contract above; the remaining services follow the same cookie pattern.
No database migration is performed here.
