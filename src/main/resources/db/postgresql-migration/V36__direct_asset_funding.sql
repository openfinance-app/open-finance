ALTER TABLE liability_tranches ADD COLUMN asset_id BIGINT REFERENCES assets(id);
