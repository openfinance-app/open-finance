CREATE TABLE asset_price_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_id INTEGER NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    price_date VARCHAR(10) NOT NULL,
    price VARCHAR(512) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    source VARCHAR(30) NOT NULL,
    UNIQUE (asset_id, price_date)
);
CREATE INDEX idx_asset_price_history_user ON asset_price_history(user_id);

-- Copy encrypted values verbatim, retaining the date on which they were observed.
INSERT INTO asset_price_history (user_id, asset_id, price_date, price, currency, source)
SELECT user_id, id, substr(last_updated, 1, 10), current_price, currency, 'RECORDED'
FROM assets
WHERE last_updated IS NOT NULL AND current_price IS NOT NULL
  AND asset_type IN ('STOCK', 'BOND', 'ETF', 'MUTUAL_FUND', 'CRYPTO', 'COMMODITY', 'OTHER');

ALTER TABLE net_worth ADD COLUMN reconstructed BOOLEAN NOT NULL DEFAULT FALSE;
-- Keep snapshots actually observed on their date; only derived older history needs rebuilding.
UPDATE net_worth SET reconstructed = TRUE WHERE snapshot_date < substr(created_at, 1, 10);
DELETE FROM net_worth WHERE reconstructed = TRUE;
