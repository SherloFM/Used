-- Apply once to each existing PostgreSQL database:
-- psql -d used -f src/main/resources/db/notification-indexes.sql

CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_unread_user
    ON notifications (user_id)
    WHERE is_read = false;
