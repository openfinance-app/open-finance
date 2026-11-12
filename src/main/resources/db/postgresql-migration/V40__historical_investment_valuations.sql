CREATE TABLE asset_price_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    price_date VARCHAR(10) NOT NULL,
    price VARCHAR(512) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    source VARCHAR(30) NOT NULL,
    UNIQUE (asset_id, price_date)
);
CREATE INDEX idx_asset_price_history_user ON asset_price_history(user_id);

INSERT INTO asset_price_history (user_id, asset_id, price_date, price, currency, source)
SELECT user_id, id, (last_updated::date)::text, current_price, currency, 'RECORDED'
FROM assets
WHERE last_updated IS NOT NULL AND current_price IS NOT NULL
  AND asset_type IN ('STOCK', 'BOND', 'ETF', 'MUTUAL_FUND', 'CRYPTO', 'COMMODITY', 'OTHER');

ALTER TABLE net_worth ADD COLUMN reconstructed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE net_worth SET reconstructed = TRUE WHERE snapshot_date::date < created_at::date;
DELETE FROM net_worth WHERE reconstructed = TRUE;
