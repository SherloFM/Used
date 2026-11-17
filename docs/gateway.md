# Browser gateway

`gateway` is an nginx container that gives the browser **one origin** for pages
and APIs. It is a router, not a business service: it owns no data, no
authentication, and no schema.

```text
Browser -> localhost:8080 (gateway)
             /                      -> frontend-service:8080
             /auth/user/**, /api/profile, /api/admin/users
                                    -> accounts-service:8081
             /api/listings|/api/categories|/api/payments|/api/wallet
                                    -> marketplace-service:8083
             /api/favorites         -> favorites-service:8084
             /api/notifications/**  -> notification-service:8085
             /api/reports, /api/admin/reports
                                    -> moderation-service:8086
             /api/chat, /ws         -> chat-service:8082
             /api/media/**          -> media-service:8088
```

Config: [`gateway/nginx.conf`](../../gateway/nginx.conf).

## Why an edge instead of CORS

The frontend calls `authenticatedFetch`, which uses `credentials: 'include'`
and an `X-XSRF-TOKEN` header taken from `GET /auth/user/csrf`. Notifications use
`new EventSource('/api/notifications/stream')`, which cannot set headers or
credentials at all.

Pointing those at `:8081`/`:8083` directly would require credentialed CORS,
`SameSite=None` on the HttpOnly `used_auth` cookie, and it would still break
`EventSource`. One origin removes the whole problem: the cookie stays host-only
on `localhost`, CSRF is unchanged, and no service needs a CORS exception for the
production path.

## Blocked at the edge

| Path | Reason |
|---|---|
| `/api/internal/**` | service-to-service API; authenticated only by `X-Internal-Token` |
| `/actuator/**` | per-service health and introspection |

Both return `404`, not `403`, so the edge does not confirm that an internal
surface exists. Longest-prefix matching keeps these ahead of the service routes,
so `/api/internal/media` cannot reach Media.

## Tuning that is deliberate

- `client_max_body_size 8m` sits above Media's 5 MB rule and Spring's 6 MB
  multipart cap, so nginx never converts a validation error into a `413`.
- `proxy_read_timeout 120s` overrides the 60 s default: a payment write or a
  moderation delist can outlast a minute on a cold database, and a `504` while
  the server still commits is worse than a slow response.
- `/api/notifications/stream` disables `proxy_buffering` and `gzip` and allows a
  one-hour read, because SSE must flush per event and stay open.
- `/ws` allows a one-hour read and relies on the global `Upgrade`/`Connection`
  headers for the protocol switch.

## Known limitations

- Upstream hostnames are now resolved lazily through Docker's embedded DNS
  (`resolver 127.0.0.11`). A service that is down or being recreated turns only
  its own routes into `502` instead of keeping the whole edge from starting, and
  a recreated service needs no gateway restart. The trade is that a typo in a
  hostname surfaces as a `502` at request time rather than a startup error.
- The edge depends on `condition: service_started`, not `service_healthy`. It
  needs the containers to exist so the names resolve; gating on health would
  hold the UI closed for as long as a Spring context takes to bind, and one
  crash-looping service would keep every page unreachable.
- No TLS here. `X-Forwarded-Proto` is already passed, so terminating TLS at this
  container later only needs `AUTH_COOKIE_SECURE=true` on the services.
- No rate limiting yet.

## Verify

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Test-Gateway.ps1
```

See `docker-setup.md` for the port map and `media-service.md` for the image path.
