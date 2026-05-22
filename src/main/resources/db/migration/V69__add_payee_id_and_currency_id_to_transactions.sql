-- Migration: Add payee_id and currency_id foreign keys to transactions
-- Links transactions to the payees and currencies tables for referential integrity
-- Date: 2026-05-22

-- Add payee_id column referencing payees table
ALTER TABLE transactions ADD COLUMN payee_id INTEGER REFERENCES payees(id) ON DELETE SET NULL;

-- Add currency_id column referencing currencies table
ALTER TABLE transactions ADD COLUMN currency_id INTEGER REFERENCES currencies(id);

-- Create indexes for the new foreign key columns
CREATE INDEX IF NOT EXISTS idx_transaction_payee_id ON transactions(payee_id);
CREATE INDEX IF NOT EXISTS idx_transaction_currency_id ON transactions(currency_id);

-- Mirror the new columns in the archive table
ALTER TABLE transactions_archive ADD COLUMN payee_id INTEGER;
ALTER TABLE transactions_archive ADD COLUMN currency_id INTEGER;
