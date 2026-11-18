BEGIN;

-- The moderation saga commits DELISTING before publishing to RabbitMQ. The
-- original database check constraint predates that state and rejects the admin
-- decision before the AFTER_COMMIT dispatcher can send its command.
ALTER TABLE listing_reports
    DROP CONSTRAINT IF EXISTS listing_reports_status_check;

ALTER TABLE listing_reports
    ADD CONSTRAINT listing_reports_status_check CHECK (
        status IN ('PENDING', 'UNDER_REVIEW', 'DISMISSED', 'ACTION_TAKEN', 'DELISTING')
    );

COMMIT;
