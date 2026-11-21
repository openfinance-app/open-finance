-- Existing rules keep the text they were written against.
-- New rules explicitly persist false through the entity.
ALTER TABLE transaction_rules ADD COLUMN use_legacy_import_text BOOLEAN NOT NULL DEFAULT TRUE;
