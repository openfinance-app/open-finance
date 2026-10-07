-- Text matches the exact-decimal JPA converter and SQLite's non-REAL storage.
-- This preserves existing rates; it cannot reconstruct precision lost before migration.
ALTER TABLE exchange_rates DROP CONSTRAINT chk_exchange_rate_positive;
ALTER TABLE exchange_rates ALTER COLUMN rate TYPE VARCHAR(512) USING rate::TEXT;
ALTER TABLE exchange_rates ADD CONSTRAINT chk_exchange_rate_positive CHECK (rate::NUMERIC > 0);
ALTER TABLE transactions ALTER COLUMN conversion_rate TYPE VARCHAR(512) USING conversion_rate::TEXT;
ALTER TABLE transactions_archive ALTER COLUMN conversion_rate TYPE VARCHAR(512) USING conversion_rate::TEXT;
