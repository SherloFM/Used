-- Apply once to each existing PostgreSQL database:
-- psql -d used -f src/main/resources/db/notification-processed-events.sql

-- Idempotency record for listing lifecycle events. The primary key IS the
-- eventId, so a redelivered message cannot send a second email.
CREATE TABLE IF NOT EXISTS processed_listing_events (
    event_id     VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(32)  NOT NULL,
    listing_id   BIGINT,
    processed_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_processed_listing_events PRIMARY KEY (event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_listing_events_processed_at
    ON processed_listing_events (processed_at);
