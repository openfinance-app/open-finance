-- =============================================================================
-- V3: Align PostgreSQL storage with encrypted BigDecimal converters
--
-- EncryptedBigDecimalConverter persists ciphertext as VARCHAR. The original
-- PostgreSQL schema incorrectly declared several of those columns as NUMERIC,
-- which SQLite accepts because it does not enforce column affinity strictly.
-- =============================================================================

-- accounts.balance and accounts.opening_balance
ALTER TABLE accounts ALTER COLUMN balance DROP DEFAULT;
ALTER TABLE accounts ALTER COLUMN opening_balance DROP DEFAULT;
ALTER TABLE accounts ALTER COLUMN balance TYPE VARCHAR(512) USING balance::TEXT;
ALTER TABLE accounts ALTER COLUMN opening_balance TYPE VARCHAR(512) USING opening_balance::TEXT;
ALTER TABLE accounts ALTER COLUMN balance SET DEFAULT '0';
ALTER TABLE accounts ALTER COLUMN opening_balance SET DEFAULT '0';

-- transactions.amount and the archive mirror
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_amount_check;
ALTER TABLE transactions ALTER COLUMN amount TYPE VARCHAR(512) USING amount::TEXT;
ALTER TABLE transactions_archive ALTER COLUMN amount TYPE VARCHAR(512) USING amount::TEXT;

-- recurring_transactions.amount
ALTER TABLE recurring_transactions DROP CONSTRAINT IF EXISTS recurring_transactions_amount_check;
ALTER TABLE recurring_transactions ALTER COLUMN amount TYPE VARCHAR(512) USING amount::TEXT;

-- assets.quantity and assets.purchase_price
ALTER TABLE assets DROP CONSTRAINT IF EXISTS chk_asset_quantity_positive;
ALTER TABLE assets DROP CONSTRAINT IF EXISTS chk_asset_purchase_price_non_negative;
ALTER TABLE assets ALTER COLUMN quantity TYPE VARCHAR(512) USING quantity::TEXT;
ALTER TABLE assets ALTER COLUMN purchase_price TYPE VARCHAR(512) USING purchase_price::TEXT;
ALTER TABLE assets ALTER COLUMN current_price TYPE NUMERIC(19, 8);

-- transaction_splits.amount
ALTER TABLE transaction_splits ALTER COLUMN amount TYPE VARCHAR(512) USING amount::TEXT;

-- net_worth totals
ALTER TABLE net_worth ALTER COLUMN total_liabilities DROP DEFAULT;
ALTER TABLE net_worth ALTER COLUMN total_assets TYPE VARCHAR(512) USING total_assets::TEXT;
ALTER TABLE net_worth ALTER COLUMN total_liabilities TYPE VARCHAR(512) USING total_liabilities::TEXT;
ALTER TABLE net_worth ALTER COLUMN net_worth TYPE VARCHAR(512) USING net_worth::TEXT;
ALTER TABLE net_worth ALTER COLUMN total_liabilities SET DEFAULT '0';

-- A payee may reference a category owned by a user. Removing that category
-- must clear the optional association rather than block user deletion.
ALTER TABLE payees DROP CONSTRAINT IF EXISTS payees_category_id_fkey;
ALTER TABLE payees ADD CONSTRAINT payees_category_id_fkey
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;
