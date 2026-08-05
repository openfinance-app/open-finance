-- Migration: Persist the original (pre-conversion) amount, currency, and rate on transactions.
-- When a transaction is entered in a currency other than its account currency, the frontend
-- converts to the account currency on save; these columns preserve what the user originally typed
-- so the edit form can restore it. All nullable — populated only when a conversion occurred.
-- original_amount holds encrypted ciphertext (VARCHAR), matching the encrypted `amount` column.
-- Date: 2026-08-05

ALTER TABLE transactions ADD COLUMN original_amount VARCHAR(512);
ALTER TABLE transactions ADD COLUMN original_currency VARCHAR(3);
ALTER TABLE transactions ADD COLUMN conversion_rate NUMERIC(18, 8);

-- Mirror the new columns in the archive table so archival does not drop them (cf. V69).
ALTER TABLE transactions_archive ADD COLUMN original_amount VARCHAR(512);
ALTER TABLE transactions_archive ADD COLUMN original_currency VARCHAR(3);
ALTER TABLE transactions_archive ADD COLUMN conversion_rate NUMERIC(18, 8);
