-- Preserve the boundary between an independent estimate and capitalized ledger movements.
-- Existing values remain untouched: encrypted financial records require explicit reconciliation.
ALTER TABLE assets ADD COLUMN valuation_recorded_at VARCHAR(40);
ALTER TABLE real_estate_value_history ADD COLUMN movement_recorded_at VARCHAR(40);
