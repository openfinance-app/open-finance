-- =============================================================================
-- V4: Align PostgreSQL date/time storage with JPA LocalDate/LocalDateTime
-- converters (LocalDateConverter / LocalDateTimeConverter)
--
-- Those converters are @Converter(autoApply = true) and persist every
-- LocalDate/LocalDateTime entity field as an ISO-8601 string. The original
-- PostgreSQL schema declared those columns as TIMESTAMP/DATE, which SQLite
-- tolerates (dynamic typing) but PostgreSQL rejects at bind time with
-- "column is of type timestamp without time zone but expression is of type
-- character varying".
--
-- Existing data is converted with to_char() so a running Railway deployment
-- keeps its timestamps as parseable ISO-8601 text.
-- =============================================================================

-- Date-ordered CHECK constraints cannot survive the type transition below
-- (DATE >= VARCHAR is invalid mid-migration); they are re-added as pure text
-- comparisons afterwards (ISO-8601 text ordering == chronological ordering).
ALTER TABLE liabilities DROP CONSTRAINT IF EXISTS chk_liability_dates_logical;
ALTER TABLE budgets      DROP CONSTRAINT IF EXISTS chk_budget_dates;

-- users
ALTER TABLE users ALTER COLUMN locked_until  DROP DEFAULT;
ALTER TABLE users ALTER COLUMN locked_until  TYPE VARCHAR(40) USING to_char(locked_until, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE users ALTER COLUMN last_login_at DROP DEFAULT;
ALTER TABLE users ALTER COLUMN last_login_at TYPE VARCHAR(40) USING to_char(last_login_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE users ALTER COLUMN created_at    DROP DEFAULT;
ALTER TABLE users ALTER COLUMN created_at    TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE users ALTER COLUMN created_at    SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE users ALTER COLUMN updated_at    DROP DEFAULT;
ALTER TABLE users ALTER COLUMN updated_at    TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- user_settings
ALTER TABLE user_settings ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE user_settings ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE user_settings ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE user_settings ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- institutions
ALTER TABLE institutions ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE institutions ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE institutions ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE institutions ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE institutions ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- accounts
ALTER TABLE accounts ALTER COLUMN opening_date DROP DEFAULT;
ALTER TABLE accounts ALTER COLUMN opening_date TYPE VARCHAR(10) USING to_char(opening_date, 'YYYY-MM-DD');
ALTER TABLE accounts ALTER COLUMN opening_date SET DEFAULT '2026-01-01';
ALTER TABLE accounts ALTER COLUMN created_at   DROP DEFAULT;
ALTER TABLE accounts ALTER COLUMN created_at   TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE accounts ALTER COLUMN created_at   SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE accounts ALTER COLUMN updated_at   DROP DEFAULT;
ALTER TABLE accounts ALTER COLUMN updated_at   TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- categories
ALTER TABLE categories ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE categories ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE categories ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE categories ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- transactions
ALTER TABLE transactions ALTER COLUMN transaction_date DROP DEFAULT;
ALTER TABLE transactions ALTER COLUMN transaction_date TYPE VARCHAR(10) USING to_char(transaction_date, 'YYYY-MM-DD');
ALTER TABLE transactions ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE transactions ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transactions ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE transactions ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- transactions_archive
ALTER TABLE transactions_archive ALTER COLUMN transaction_date DROP DEFAULT;
ALTER TABLE transactions_archive ALTER COLUMN transaction_date TYPE VARCHAR(10) USING to_char(transaction_date, 'YYYY-MM-DD');
ALTER TABLE transactions_archive ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE transactions_archive ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transactions_archive ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE transactions_archive ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transactions_archive ALTER COLUMN archived_at DROP DEFAULT;
ALTER TABLE transactions_archive ALTER COLUMN archived_at TYPE VARCHAR(40) USING to_char(archived_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transactions_archive ALTER COLUMN archived_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- net_worth
ALTER TABLE net_worth ALTER COLUMN snapshot_date DROP DEFAULT;
ALTER TABLE net_worth ALTER COLUMN snapshot_date TYPE VARCHAR(10) USING to_char(snapshot_date, 'YYYY-MM-DD');
ALTER TABLE net_worth ALTER COLUMN created_at   DROP DEFAULT;
ALTER TABLE net_worth ALTER COLUMN created_at   TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE net_worth ALTER COLUMN created_at   SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- assets
ALTER TABLE assets ALTER COLUMN purchase_date       DROP DEFAULT;
ALTER TABLE assets ALTER COLUMN purchase_date       TYPE VARCHAR(10) USING to_char(purchase_date, 'YYYY-MM-DD');
ALTER TABLE assets ALTER COLUMN last_updated        DROP DEFAULT;
ALTER TABLE assets ALTER COLUMN last_updated        TYPE VARCHAR(40) USING to_char(last_updated, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE assets ALTER COLUMN warranty_expiration DROP DEFAULT;
ALTER TABLE assets ALTER COLUMN warranty_expiration TYPE VARCHAR(10) USING to_char(warranty_expiration, 'YYYY-MM-DD');
ALTER TABLE assets ALTER COLUMN created_at          DROP DEFAULT;
ALTER TABLE assets ALTER COLUMN created_at          TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE assets ALTER COLUMN created_at          SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE assets ALTER COLUMN updated_at          DROP DEFAULT;
ALTER TABLE assets ALTER COLUMN updated_at          TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- liabilities
ALTER TABLE liabilities ALTER COLUMN start_date DROP DEFAULT;
ALTER TABLE liabilities ALTER COLUMN start_date TYPE VARCHAR(10) USING to_char(start_date, 'YYYY-MM-DD');
ALTER TABLE liabilities ALTER COLUMN end_date   DROP DEFAULT;
ALTER TABLE liabilities ALTER COLUMN end_date   TYPE VARCHAR(10) USING to_char(end_date, 'YYYY-MM-DD');
ALTER TABLE liabilities ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE liabilities ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE liabilities ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE liabilities ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE liabilities ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE liabilities ALTER COLUMN updated_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- currencies
ALTER TABLE currencies ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE currencies ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE currencies ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE currencies ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE currencies ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE currencies ALTER COLUMN updated_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- exchange_rates
ALTER TABLE exchange_rates ALTER COLUMN rate_date  DROP DEFAULT;
ALTER TABLE exchange_rates ALTER COLUMN rate_date  TYPE VARCHAR(10) USING to_char(rate_date, 'YYYY-MM-DD');
ALTER TABLE exchange_rates ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE exchange_rates ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE exchange_rates ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- payees
ALTER TABLE payees ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE payees ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE payees ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE payees ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE payees ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- budgets
ALTER TABLE budgets ALTER COLUMN start_date DROP DEFAULT;
ALTER TABLE budgets ALTER COLUMN start_date TYPE VARCHAR(10) USING to_char(start_date, 'YYYY-MM-DD');
ALTER TABLE budgets ALTER COLUMN end_date   DROP DEFAULT;
ALTER TABLE budgets ALTER COLUMN end_date   TYPE VARCHAR(10) USING to_char(end_date, 'YYYY-MM-DD');
ALTER TABLE budgets ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE budgets ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE budgets ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE budgets ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE budgets ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE budgets ALTER COLUMN updated_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- budget_alerts
ALTER TABLE budget_alerts ALTER COLUMN last_triggered DROP DEFAULT;
ALTER TABLE budget_alerts ALTER COLUMN last_triggered TYPE VARCHAR(40) USING to_char(last_triggered, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE budget_alerts ALTER COLUMN created_at      DROP DEFAULT;
ALTER TABLE budget_alerts ALTER COLUMN created_at      TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE budget_alerts ALTER COLUMN created_at      SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE budget_alerts ALTER COLUMN updated_at      DROP DEFAULT;
ALTER TABLE budget_alerts ALTER COLUMN updated_at      TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE budget_alerts ALTER COLUMN updated_at      SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- real_estate_properties
ALTER TABLE real_estate_properties ALTER COLUMN purchase_date DROP DEFAULT;
ALTER TABLE real_estate_properties ALTER COLUMN purchase_date TYPE VARCHAR(10) USING to_char(purchase_date, 'YYYY-MM-DD');
ALTER TABLE real_estate_properties ALTER COLUMN created_at    DROP DEFAULT;
ALTER TABLE real_estate_properties ALTER COLUMN created_at    TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE real_estate_properties ALTER COLUMN created_at    SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE real_estate_properties ALTER COLUMN updated_at    DROP DEFAULT;
ALTER TABLE real_estate_properties ALTER COLUMN updated_at    TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE real_estate_properties ALTER COLUMN updated_at    SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- real_estate_value_history
ALTER TABLE real_estate_value_history ALTER COLUMN effective_date DROP DEFAULT;
ALTER TABLE real_estate_value_history ALTER COLUMN effective_date TYPE VARCHAR(10) USING to_char(effective_date, 'YYYY-MM-DD');
ALTER TABLE real_estate_value_history ALTER COLUMN created_at     DROP DEFAULT;
ALTER TABLE real_estate_value_history ALTER COLUMN created_at     TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE real_estate_value_history ALTER COLUMN created_at     SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- real_estate_simulations
ALTER TABLE real_estate_simulations ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE real_estate_simulations ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE real_estate_simulations ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE real_estate_simulations ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE real_estate_simulations ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE real_estate_simulations ALTER COLUMN updated_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- ai_conversations
ALTER TABLE ai_conversations ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE ai_conversations ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE ai_conversations ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE ai_conversations ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE ai_conversations ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE ai_conversations ALTER COLUMN updated_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- insights
ALTER TABLE insights ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE insights ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE insights ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- attachments
ALTER TABLE attachments ALTER COLUMN uploaded_at DROP DEFAULT;
ALTER TABLE attachments ALTER COLUMN uploaded_at TYPE VARCHAR(40) USING to_char(uploaded_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE attachments ALTER COLUMN uploaded_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- recurring_transactions
ALTER TABLE recurring_transactions ALTER COLUMN next_occurrence DROP DEFAULT;
ALTER TABLE recurring_transactions ALTER COLUMN next_occurrence TYPE VARCHAR(10) USING to_char(next_occurrence, 'YYYY-MM-DD');
ALTER TABLE recurring_transactions ALTER COLUMN end_date        DROP DEFAULT;
ALTER TABLE recurring_transactions ALTER COLUMN end_date        TYPE VARCHAR(10) USING to_char(end_date, 'YYYY-MM-DD');
ALTER TABLE recurring_transactions ALTER COLUMN created_at      DROP DEFAULT;
ALTER TABLE recurring_transactions ALTER COLUMN created_at      TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE recurring_transactions ALTER COLUMN updated_at      DROP DEFAULT;
ALTER TABLE recurring_transactions ALTER COLUMN updated_at      TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- backups
ALTER TABLE backups ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE backups ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE backups ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE backups ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE backups ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- import_sessions
ALTER TABLE import_sessions ALTER COLUMN created_at   DROP DEFAULT;
ALTER TABLE import_sessions ALTER COLUMN created_at   TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE import_sessions ALTER COLUMN created_at   SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE import_sessions ALTER COLUMN updated_at   DROP DEFAULT;
ALTER TABLE import_sessions ALTER COLUMN updated_at   TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE import_sessions ALTER COLUMN updated_at   SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE import_sessions ALTER COLUMN completed_at DROP DEFAULT;
ALTER TABLE import_sessions ALTER COLUMN completed_at TYPE VARCHAR(40) USING to_char(completed_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- The BEFORE UPDATE trigger must write ISO-8601 text (the column is no longer
-- a TIMESTAMP; CURRENT_TIMESTAMP would be stored using PostgreSQL's text
-- representation, which the JPA converter cannot parse back).
CREATE OR REPLACE FUNCTION update_import_session_timestamp() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- interest_rate_variations
ALTER TABLE interest_rate_variations ALTER COLUMN valid_from  DROP DEFAULT;
ALTER TABLE interest_rate_variations ALTER COLUMN valid_from  TYPE VARCHAR(10) USING to_char(valid_from, 'YYYY-MM-DD');
ALTER TABLE interest_rate_variations ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE interest_rate_variations ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE interest_rate_variations ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE interest_rate_variations ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE interest_rate_variations ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- transaction_splits
ALTER TABLE transaction_splits ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE transaction_splits ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transaction_splits ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transaction_splits ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE transaction_splits ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- transaction_rules
ALTER TABLE transaction_rules ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE transaction_rules ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transaction_rules ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transaction_rules ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE transaction_rules ALTER COLUMN updated_at TYPE VARCHAR(40) USING to_char(updated_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE transaction_rules ALTER COLUMN updated_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- operation_history
ALTER TABLE operation_history ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE operation_history ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE operation_history ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE operation_history ALTER COLUMN undone_at  DROP DEFAULT;
ALTER TABLE operation_history ALTER COLUMN undone_at  TYPE VARCHAR(40) USING to_char(undone_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE operation_history ALTER COLUMN redone_at  DROP DEFAULT;
ALTER TABLE operation_history ALTER COLUMN redone_at  TYPE VARCHAR(40) USING to_char(redone_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- security_audit_log
ALTER TABLE security_audit_log ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE security_audit_log ALTER COLUMN created_at TYPE VARCHAR(40) USING to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SS.US');
ALTER TABLE security_audit_log ALTER COLUMN created_at SET DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS.US');

-- Re-add date-ordered constraints as text comparisons (ISO-8601 text order
-- equals chronological order for the same format).
ALTER TABLE liabilities ADD CONSTRAINT chk_liability_dates_logical
    CHECK (end_date IS NULL OR end_date >= start_date);
ALTER TABLE budgets ADD CONSTRAINT chk_budget_dates
    CHECK (end_date >= start_date);