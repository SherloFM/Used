-- Media owns this table. Apply it to the shared `used` database before uploads.
-- Image bytes are immutable: replacing an image creates a new row and URL.
CREATE TABLE IF NOT EXISTS media_assets (
    id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS media_assets_owner_id_idx ON media_assets (owner_id);
