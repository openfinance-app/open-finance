-- PostgreSQL already used text-compatible columns since V3. Keep both schemas aligned.
ALTER TABLE accounts ALTER COLUMN balance TYPE VARCHAR(512) USING balance::TEXT;
ALTER TABLE accounts ALTER COLUMN opening_balance TYPE VARCHAR(512) USING opening_balance::TEXT;
ALTER TABLE transactions ALTER COLUMN amount TYPE VARCHAR(512) USING amount::TEXT;
ALTER TABLE transactions_archive ALTER COLUMN amount TYPE VARCHAR(512) USING amount::TEXT;
ALTER TABLE recurring_transactions ALTER COLUMN amount TYPE VARCHAR(512) USING amount::TEXT;
