-- A gift's contribution to wealth is distinct from subsequent investment performance.
-- Unknown historical receipt values remain null; do not infer them from current values.
ALTER TABLE assets ADD COLUMN received_value VARCHAR(512);
ALTER TABLE real_estate_properties ADD COLUMN received_value VARCHAR(512);
