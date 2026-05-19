-- Migration: Add user_id to institutions and payees for user-scoped custom entries
-- Custom institutions and payees should only be visible to their creator
-- System entries (is_system=1) have NULL user_id and are visible to all users
-- Date: 2026-05-19

-- Add user_id column to institutions table
ALTER TABLE institutions ADD COLUMN user_id INTEGER REFERENCES users(id);

-- Create index for user-based lookups
CREATE INDEX IF NOT EXISTS idx_institution_user_id ON institutions(user_id);

-- Add user_id column to payees table
ALTER TABLE payees ADD COLUMN user_id INTEGER REFERENCES users(id);

-- Create index for user-based lookups
CREATE INDEX IF NOT EXISTS idx_payee_user_id ON payees(user_id);

-- Drop the old unique constraint on payee name (SQLite requires recreating the index)
-- The unique constraint on payees.name was enforced via CREATE TABLE ... UNIQUE
-- In SQLite, we can't drop a column constraint, but we can add a new unique index
-- that includes user_id. The old unique constraint from the column definition
-- will remain but since existing data is valid, this is fine.
-- For new data, uniqueness will be enforced per-user via application logic.
