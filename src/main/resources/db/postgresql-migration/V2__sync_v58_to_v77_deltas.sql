-- =============================================================================
-- V2: Bring the consolidated PostgreSQL schema up to date with SQLite
-- migrations V58-V77 (V1 above only tracked up to V57, with a few later
-- columns merged in ad-hoc). Mirrors each SQLite migration's net schema
-- effect; see src/main/resources/db/migration/V58..V77 for the originals.
-- =============================================================================

-- V58: deactivate special interbank/settlement currencies (data-only)
UPDATE currencies SET is_active = FALSE WHERE code IN ('CHE', 'CHW', 'CLF', 'COU');

-- V59: extend liability type constraint with STUDENT_LOAN, AUTO_LOAN
ALTER TABLE liabilities DROP CONSTRAINT IF EXISTS chk_liability_type_valid;
ALTER TABLE liabilities ADD CONSTRAINT chk_liability_type_valid
    CHECK (type IN ('LOAN', 'MORTGAGE', 'CREDIT_CARD', 'PERSONAL_LOAN', 'STUDENT_LOAN', 'AUTO_LOAN', 'OTHER'));

-- V61 + V77: widen attachments.entity_type, add RECURRING_TRANSACTION, store file
-- bytes in the database instead of a filesystem path (BYTEA is Postgres's native
-- binary column type, equivalent to SQLite's BLOB).
ALTER TABLE attachments ALTER COLUMN entity_type TYPE VARCHAR(25);
ALTER TABLE attachments DROP CONSTRAINT IF EXISTS chk_attachment_entity_type;
ALTER TABLE attachments ADD CONSTRAINT chk_attachment_entity_type
    CHECK (entity_type IN ('TRANSACTION', 'ASSET', 'REAL_ESTATE', 'LIABILITY', 'ACCOUNT', 'RECURRING_TRANSACTION'));
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS file_data BYTEA;
ALTER TABLE attachments DROP COLUMN IF EXISTS file_path;
ALTER TABLE attachments ALTER COLUMN file_data SET NOT NULL;

-- V62: onboarding flag on users (existing rows are already onboarded)
ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_complete BOOLEAN NOT NULL DEFAULT TRUE;

-- V63 + V73: user-facing display preferences
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS amount_display_mode VARCHAR(10) NOT NULL DEFAULT 'base';
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS decimal_places_override_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS preferred_decimal_places INTEGER NOT NULL DEFAULT 2;

-- V64: OR-logic support for transaction rules
ALTER TABLE transaction_rules ADD COLUMN IF NOT EXISTS condition_match VARCHAR(3) NOT NULL DEFAULT 'AND'
    CHECK (condition_match IN ('AND', 'OR'));

-- V65: real estate value history (for point-in-time net worth snapshots)
CREATE TABLE IF NOT EXISTS real_estate_value_history (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    property_id    BIGINT NOT NULL REFERENCES real_estate_properties(id) ON DELETE CASCADE,
    user_id        BIGINT NOT NULL REFERENCES users(id),
    effective_date DATE NOT NULL,
    recorded_value TEXT NOT NULL,
    currency       TEXT NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_re_value_history_property_date
    ON real_estate_value_history(property_id, effective_date);

-- V67: undo/redo operation history
CREATE TABLE IF NOT EXISTS operation_history (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type          TEXT NOT NULL,
    entity_id            BIGINT,
    entity_label         TEXT,
    operation_type       TEXT NOT NULL CHECK (operation_type IN ('CREATE', 'UPDATE', 'DELETE')),
    entity_snapshot_json TEXT,
    changed_fields_json  TEXT,
    undone_at            TIMESTAMP,
    redone_at            TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_op_history_user_id    ON operation_history(user_id);
CREATE INDEX IF NOT EXISTS idx_op_history_created_at ON operation_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_op_history_entity     ON operation_history(entity_type, entity_id);

-- V68: user-scoped custom institutions/payees (NULL user_id = shared system entry)
ALTER TABLE institutions ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);
CREATE INDEX IF NOT EXISTS idx_institution_user_id ON institutions(user_id);

ALTER TABLE payees ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);
CREATE INDEX IF NOT EXISTS idx_payee_user_id ON payees(user_id);

-- V69: payee/currency foreign keys on transactions (+ archive mirror)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS payee_id BIGINT REFERENCES payees(id) ON DELETE SET NULL;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_transaction_payee_id    ON transactions(payee_id);
CREATE INDEX IF NOT EXISTS idx_transaction_currency_id ON transactions(currency_id);

ALTER TABLE transactions_archive ADD COLUMN IF NOT EXISTS payee_id BIGINT;
ALTER TABLE transactions_archive ADD COLUMN IF NOT EXISTS currency_id BIGINT;

-- V70: currency_id foreign keys on every entity that stores currency as a plain string
ALTER TABLE recurring_transactions ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_recurring_transaction_currency_id ON recurring_transactions(currency_id);

ALTER TABLE budgets ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_budget_currency_id ON budgets(currency_id);

ALTER TABLE assets ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_asset_currency_id ON assets(currency_id);

ALTER TABLE liabilities ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_liability_currency_id ON liabilities(currency_id);

ALTER TABLE real_estate_properties ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_real_estate_currency_id ON real_estate_properties(currency_id);

ALTER TABLE net_worth ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_net_worth_currency_id ON net_worth(currency_id);

ALTER TABLE real_estate_value_history ADD COLUMN IF NOT EXISTS currency_id BIGINT REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_re_value_history_currency_id ON real_estate_value_history(currency_id);

-- V71: replace the FTS mechanism with blind-index search tokens (HMAC-based, not
-- reversible) - V1's tsvector-based transactions_fts table was never used by the
-- application layer (SearchTokenService targets `search_tokens` by name) and would
-- have stored plaintext of encrypted fields, the same security issue V71 fixed on
-- SQLite. Drop it and create the table the app actually reads/writes.
DROP TABLE IF EXISTS transactions_fts;

CREATE TABLE IF NOT EXISTS search_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type VARCHAR(30) NOT NULL,
    entity_id   BIGINT NOT NULL,
    field_name  VARCHAR(30) NOT NULL,
    token       VARCHAR(16) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_search_tokens_lookup ON search_tokens(user_id, token, entity_type);
CREATE INDEX IF NOT EXISTS idx_search_tokens_entity  ON search_tokens(entity_type, entity_id);
