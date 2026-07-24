-- V73: Add user-configurable decimal-places display preference to user_settings.
-- decimal_places_override_enabled: when 1, preferred_decimal_places overrides the
--   default per-currency decimal count for ALL currencies (display only).
-- preferred_decimal_places: number of fraction digits to show when the override is
--   enabled. Valid range 1-8 (enforced by Bean Validation at the API boundary; no DB
--   CHECK constraint because SQLite cannot easily add/alter CHECK constraints).
-- Display-only: this preference never affects stored amounts or calculations.

ALTER TABLE user_settings ADD COLUMN decimal_places_override_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_settings ADD COLUMN preferred_decimal_places INTEGER NOT NULL DEFAULT 2;
