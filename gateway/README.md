# Gateway config

`nginx.conf` in this directory is mounted into the `gateway` container at
`/etc/nginx/conf.d/default.conf`. The container is `nginx:1.29-alpine`.

Routing rules, blocked paths, and the reasoning are in
[docs/gateway.md](../../docs/gateway.md).

To add a browser-facing API path: add a `location` here, then record it in
`docs/architecture-decisions.md` and assert it in `scripts/Test-Gateway.ps1`.

Reload without dropping connections:

```powershell
docker compose exec gateway nginx -t && docker compose exec gateway nginx -s reload
```

Restart after a service is recreated with a new address:

```powershell
docker compose restart gateway
```
