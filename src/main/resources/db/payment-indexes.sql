-- ===========================================================================
-- Payment process schema additions (Step 2)
--
-- WHY THIS FILE EXISTS:
-- Spring's ddl-auto=update (dev) can create tables and plain unique
-- constraints, but it CANNOT create a PARTIAL unique index (an index with a
-- WHERE clause). Layer-4 of the double-sell defense needs exactly that.
-- Run this once against your database (dev and prod):
--
--   psql -d used_marketplace_db -f src/main/resources/db/payment-indexes.sql
--
-- Better: adopt Flyway and move this into db/migration/V2__payment_indexes.sql
-- (prod uses ddl-auto=validate, so schema changes there MUST be manual SQL
-- anyway).
-- ===========================================================================

-- Layer 4: at most ONE active payment per listing, enforced by the database.
-- Two racing "Buy" requests: the second INSERT fails, not the second buyer's money.
CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_active_listing
    ON payments (listing_id)
    WHERE status IN ('PENDING', 'PROCESSING');

-- Idempotency lookups: "does this buyer already have an active payment?"
CREATE INDEX IF NOT EXISTS idx_payments_buyer_status
    ON payments (buyer_id, status);

-- Seller's payment history page.
CREATE INDEX IF NOT EXISTS idx_payments_seller
    ON payments (seller_id);

-- One wallet per user is already enforced by the unique FK on
-- user_wallets.user_id (Hibernate created it with the table). This index is
-- added only for balance-lookup speed on large datasets.
CREATE INDEX IF NOT EXISTS idx_user_wallets_user_id
    ON user_wallets (user_id);
