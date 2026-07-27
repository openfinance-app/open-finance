-- Migration V12: Add base_currency field to users table
-- Sprint 6 - Task 6.2.13: Base currency user setting
-- Purpose: Allow users to specify their preferred base currency for multi-currency conversion
-- Author: Open Finance Development Team
-- Date: 2026-02-02

-- Add base_currency column with default value 'EUR'
-- In SQLite, we add the column with a default value.
-- Note: SQLite limited ALTER TABLE support means we add the constraint in the column definition
-- The default matches the application base currency (application.exchange-rates.base-currency: EUR,
-- surfaced via DefaultCurrencyProvider) so the DB-level default and the app-level default cannot
-- diverge.
ALTER TABLE users ADD COLUMN base_currency VARCHAR(10) NOT NULL DEFAULT 'EUR' 
    CHECK (LENGTH(base_currency) >= 3 AND LENGTH(base_currency) <= 10 AND base_currency = UPPER(base_currency) AND base_currency NOT GLOB '*[^A-Z]*');

-- Create index for base_currency for potential future analytics queries
CREATE INDEX idx_users_base_currency ON users(base_currency);

-- Update existing users to have EUR as base currency (already set by DEFAULT, but explicit for clarity)
UPDATE users SET base_currency = 'EUR' WHERE base_currency IS NULL;
