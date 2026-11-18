# Secret removal and rotation

Literal development credentials were removed from tracked properties on
19 November 2026. Configuration now resolves `DB_PASSWORD`, `SMTP_USERNAME`,
`SMTP_PASSWORD`, and `JWT_SECRET` from the environment.

Removing a value from the latest commit does not revoke it and does not erase it
from Git history. Complete every applicable rotation below.

## Gmail App Password

1. Google Account → Security → App passwords.
2. Revoke the App Password that was previously committed.
3. Create a replacement only if SMTP is still required.
4. Store it in local `.env` or `/etc/used/used.env` as `SPRING_MAIL_PASSWORD`;
   never place it in a properties file or command recorded in shell history.
5. Restart Notification and verify email delivery.

## JWT signing secret

Generate a new Base64 value directly on the target host:

```bash
openssl rand -base64 48
```

Update `JWT_SECRET` in the protected environment file and restart all services
that issue or validate JWTs. Rotation invalidates existing login cookies, so do
it during a controlled window.

## Database credentials

If the committed development password was ever reused, change the PostgreSQL
role password first and then update the protected environment value. Merely
changing `POSTGRES_PASSWORD` does not alter a role inside an existing Docker
volume.

## Repository history

The old values remain in historical commits. Rewriting history requires a
coordinated `git filter-repo` operation and force push, invalidating existing
clones and commit references. Do this only after every exposed credential has
already been revoked. GitHub secret scanning should also be enabled.

## Verification

```bash
bash deploy/ci/check-tracked-secrets.sh
```

This gate prevents a tracked `.env` and literal datasource/mail/JWT secrets in
properties files. It supplements—rather than replaces—provider-side rotation.
