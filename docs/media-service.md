# Media service

## Responsibility

Media owns image bytes and image metadata. Accounts and Marketplace no longer
store new image bytes in their own tables; they keep only the immutable URL
returned by Media.

```text
Browser -> Accounts or Marketplace
             authenticate the user
             check profile/listing ownership
             POST /api/internal/media  (X-Internal-Token)
                 Media verifies the file, stores it, returns a URL
             save the URL in the existing imgUrl column
Browser -> GET /api/media/{id} -> bytes
```

Browsers never upload directly to Media. This preserves the ownership checks in
Accounts and Marketplace, which Media cannot perform because it does not own users
or listings.

## API

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/internal/media` | `X-Internal-Token` | Store one validated image |
| `GET` | `/api/media/{id}` | Public | Read an immutable image |

Upload is `multipart/form-data` with two parts:

| Part | Type | Meaning |
|---|---|---|
| `ownerId` | positive integer | Uploader user id, recorded as scalar audit metadata |
| `file` | file | JPEG or PNG image, at most 5 MB |

Response (`201 Created`):

```json
{
  "id": "70d85413-78d7-407f-9562-5df075cf92cb",
  "path": "/api/media/70d85413-78d7-407f-9562-5df075cf92cb",
  "url": "http://localhost:8088/api/media/70d85413-78d7-407f-9562-5df075cf92cb",
  "contentType": "image/png",
  "sizeBytes": 148230,
  "sha256": "…64 hex characters…"
}
```

`url` is built from Media's `media.public-base-url`. Set that property empty when
a gateway serves `/api/media/**` on the frontend origin; `path` is always
relative and can be stored instead once routing exists.

Retrieval returns:

```http
Content-Type: image/png
Content-Length: 148230
X-Content-Type-Options: nosniff
ETag: "…sha256…"
Cache-Control: public, max-age=31536000, immutable
```

A matching `If-None-Match` returns `304 Not Modified`.

## Validation

- Maximum file size: 5 MB (enforced by multipart limits and again in the service).
- Allowed formats: JPEG and PNG only.
- The format is derived from the bytes, not from the filename or declared MIME type.
- The image must decode successfully through `ImageIO`.
- Maximum dimensions: 8000 x 8000 and 40 megapixels.
- Empty files, text pretending to be an image, GIF/WebP, corrupt data, and
  dimension bombs are rejected with `400` (or `413` for oversize uploads).

The original file is stored as uploaded. Media does not re-encode images, strip
EXIF metadata, scan for malware, or generate thumbnails.

## Storage

Media owns one table in the shared `used` database:

```sql
CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

The authoritative DDL is
`services/media-service/src/main/resources/db/media-assets.sql` and is idempotent.
Apply it before the first upload:

```powershell
Get-Content services/media-service/src/main/resources/db/media-assets.sql -Raw |
  docker compose exec -T postgres_db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f -'
```

`owner_id` is not a cross-service foreign key. Media rows are immutable: changing
an image uploads a new row and stores the new URL in Accounts or Marketplace.

PostgreSQL `bytea` is the current store so the phase stays simple and works with
RDS later. Moving to S3 changes Media's storage implementation, not the API.

## Failure behavior

Services call Media before changing their own row:

- If Media rejects the upload or is unreachable, the previous image URL and bytes
  remain untouched.
- If Media stores an asset but the owner's database update fails, an unreferenced
  asset remains. There is no distributed transaction and no automatic cleanup.
- Upload is deliberately **not** wrapped in the listing/profile transaction, so a
  slow external call does not hold a database row lock. A concurrent image upload
  can therefore win the last write; Media may retain both assets.

## Frontend

New uploads return `imgUrl`. Templates prefer it and keep a Base64 fallback so
existing rows that still contain `img`/`imgtype` keep rendering:

```javascript
if (data.imgUrl) {
    image.src = data.imgUrl;
} else if (data.img && data.imgtype) {
    image.src = `data:${data.imgtype};base64,${data.img}`;
}
```

Updated pages: listing grid, listing detail, profile avatar, payment summary.
The navbar already preferred `imgUrl`.

## Configuration

Media:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8088` | HTTP port |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | — / `5432` / — | PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | — | Credentials |
| `INTERNAL_API_TOKEN` | — | Required, at least 32 characters |
| `MEDIA_PUBLIC_BASE_URL` | `http://localhost:8088` | Prefix used in stored URLs |

Accounts and Marketplace:

| Variable | Docker value | Purpose |
|---|---|---|
| `MEDIA_BASE_URL` | `http://media-service:8088` | Internal upload endpoint |

## Runtime verification

`scripts/Test-MediaService.ps1` runs the whole HTTP contract against a live
container. It generates a real PNG, then asserts each status code:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Test-MediaService.ps1
```

Recorded result on 2026-11-16 against `http://localhost:8088`:

```text
MEDIA_SMOKE_OK id=69fb6370-4cd4-46bd-b821-4960354fab30
  status=201 retrieval=200 etag=304 stale-etag=200 invalid=400 unauthorized=401
```

That run establishes:

| Check | Evidence |
|---|---|
| Health | `GET /actuator/health` -> `200 {"status":"UP"}` |
| Token enforcement | upload without `X-Internal-Token` -> `401` |
| Byte-level validation | a `.txt` upload -> `400` |
| Upload | PNG upload -> `201` with `id`, `path`, `url` |
| Retrieval | `GET url` -> `200`, `Content-Type: image/png`, `nosniff`, `immutable` |
| Integrity | SHA-256 of the download equals the uploaded bytes |
| Conditional GET | matching `If-None-Match` -> `304`, stale ETag -> `200` |
| Persistence | `select count(*) from media_assets` -> `1` row, `image/png` |

Direct upload proves Media only. A complete profile/listing image change also
requires users and listings in the database, which is why end-to-end workflow
verification is still pending.

### Startup troubleshooting

If Media fails with `Unable to determine Dialect without JDBC metadata (please set
'jakarta.persistence.jdbc.url' ...)`, the datasource properties are usually fine
and the real cause is earlier in the log. Search for `SQLState`:

```text
WARN  HHH000247: ErrorCode: 0, SQLState: 57P03
WARN  FATAL: the database system is not yet accepting connections
WARN  HHH100046: Could not obtain connection to query JDBC database metadata
```

`57P03` is PostgreSQL still in crash recovery after an unclean shutdown. Hibernate
cannot read JDBC metadata, so it reports the misleading dialect message. Confirm
with `docker compose exec -T postgres_db pg_isready -U used -d used` and restart
Media once recovery finishes.

## Automated verification

Media is the one module whose complete test suite is currently green:

```powershell
.\mvnw.cmd -B -ntp -pl services/media-service test
```

Recorded: **14 tests pass**. They cover verified PNG/JPEG storage, format sniffing,
rejection of text/GIF/oversized/empty input, URL construction, ETag/304 behavior,
and internal-token filtering. The context test boots the application against H2
with the real entity mapping and security configuration.

Accounts gained `MediaClientTest` and `UserProfileServiceTest`; Marketplace gained
`ListingServiceImageTest` (plus its existing delist tests). Those modules still
contain stale monolith-era tests that block a normal module-wide `test` run, so
these were executed through an isolated harness: **4 Accounts tests** and **11
Marketplace tests** passed (11 includes the 7 existing delist tests). These are
isolated unit/integration results, not a full workflow.

## Known gaps

- No delete or garbage-collection endpoint yet.
- No image resizing, thumbnails, format conversion, or EXIF stripping.
- Fixed host port `8088`; a gateway must route `/api/media/**` before frontend and
  Media share an origin.
- No rate limiting on the internal endpoint beyond network/token isolation.
