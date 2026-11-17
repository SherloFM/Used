-- One-time setup for per-user chat deletion ("hidden_for_*" flags).
-- Hibernate's ddl-auto=update cannot add NOT NULL boolean columns to a
-- table that already contains rows (PostgreSQL requires a default), so
-- run this script once on existing databases: psql -U postgres -d used -f db/chat-conversation-visibility.sql
ALTER TABLE chat_conversations ADD COLUMN IF NOT EXISTS hidden_for_buyer  boolean NOT NULL DEFAULT false;
ALTER TABLE chat_conversations ADD COLUMN IF NOT EXISTS hidden_for_seller boolean NOT NULL DEFAULT false;
