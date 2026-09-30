-- Date/time columns use the application's ISO string converter on both databases.
ALTER TABLE assets ADD COLUMN valuation_recorded_at VARCHAR(40);
ALTER TABLE real_estate_value_history ADD COLUMN movement_recorded_at VARCHAR(40);
