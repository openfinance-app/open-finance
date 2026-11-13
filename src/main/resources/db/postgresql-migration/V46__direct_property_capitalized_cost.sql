-- Monetary values use the owner's encryption key, like drawn_amount.
ALTER TABLE liability_tranches ADD COLUMN capitalized_amount TEXT;
ALTER TABLE liability_tranches ADD COLUMN valuation_change TEXT;
