-- Store public prices/rates and encrypted or plaintext amounts without a fixed scale.
-- Already-rounded historical values are preserved; their lost digits cannot be recovered.
ALTER TABLE assets DROP CONSTRAINT IF EXISTS chk_asset_current_price_non_negative;
ALTER TABLE assets ALTER COLUMN quantity TYPE VARCHAR(512) USING quantity::TEXT;
ALTER TABLE assets ALTER COLUMN purchase_price TYPE VARCHAR(512) USING purchase_price::TEXT;
ALTER TABLE assets ALTER COLUMN current_price TYPE VARCHAR(512) USING current_price::TEXT;
ALTER TABLE assets ADD CONSTRAINT chk_asset_current_price_non_negative
    CHECK (current_price::NUMERIC >= 0);
ALTER TABLE transaction_splits ALTER COLUMN amount TYPE VARCHAR(512) USING amount::TEXT;
ALTER TABLE net_worth ALTER COLUMN total_assets TYPE VARCHAR(512) USING total_assets::TEXT;
ALTER TABLE net_worth ALTER COLUMN total_liabilities TYPE VARCHAR(512) USING total_liabilities::TEXT;
ALTER TABLE net_worth ALTER COLUMN net_worth TYPE VARCHAR(512) USING net_worth::TEXT;
ALTER TABLE interest_rate_variations ALTER COLUMN tax_rate DROP DEFAULT;
ALTER TABLE interest_rate_variations ALTER COLUMN rate TYPE VARCHAR(512) USING rate::TEXT;
ALTER TABLE interest_rate_variations ALTER COLUMN tax_rate TYPE VARCHAR(512) USING tax_rate::TEXT;
ALTER TABLE interest_rate_variations ALTER COLUMN tax_rate SET DEFAULT '0';
