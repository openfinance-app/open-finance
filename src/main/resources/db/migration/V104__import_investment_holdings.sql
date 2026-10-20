-- Keep investment units and opening positions separate from historical monetary values.
ALTER TABLE transactions ADD COLUMN investment_quantity VARCHAR(512);
ALTER TABLE transactions_archive ADD COLUMN investment_quantity VARCHAR(512);
ALTER TABLE transactions ADD COLUMN investment_cash BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE transactions_archive ADD COLUMN investment_cash BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE assets ADD COLUMN import_source VARCHAR(100);
ALTER TABLE assets ADD COLUMN import_opening_quantity VARCHAR(512);
ALTER TABLE assets ADD COLUMN import_opening_cost VARCHAR(512);
CREATE UNIQUE INDEX idx_asset_import_source ON assets(user_id, account_id, import_source);
