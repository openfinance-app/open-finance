-- =============================================================================
-- V1: Consolidated PostgreSQL Schema for Open-Finance
-- Generated from SQLite migrations V1–V57 for Railway cloud deployment
--
-- This single migration creates the complete database schema on a fresh
-- PostgreSQL instance. It is NOT meant to run against the SQLite database.
-- Activate with: spring.flyway.locations=classpath:db/migration/postgresql
-- =============================================================================

-- ── 1. schema_info ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS schema_info (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version         VARCHAR(50) NOT NULL,
    description     VARCHAR(255),
    installed_on    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    execution_time  INTEGER,
    success         BOOLEAN DEFAULT TRUE
);

INSERT INTO schema_info (version, description, execution_time, success)
VALUES ('V1', 'Consolidated PostgreSQL schema', 0, TRUE);

-- ── 2. system_settings ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_settings (
    setting_key     VARCHAR(100) PRIMARY KEY,
    setting_value   TEXT,
    description     VARCHAR(255),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
    ('app_version',     '0.1.0',            'Application version'),
    ('schema_version',  'V1-pg',            'Current database schema version'),
    ('initialized_at',  CURRENT_TIMESTAMP::TEXT, 'Database initialization timestamp');

-- ── 3. users ────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username                VARCHAR(50) NOT NULL UNIQUE,
    email                   VARCHAR(255) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    master_password_salt    VARCHAR(255) NOT NULL,
    base_currency           VARCHAR(10) NOT NULL DEFAULT 'USD',
    secondary_currency      VARCHAR(3),
    profile_image           TEXT,
    failed_login_attempts   INTEGER NOT NULL DEFAULT 0,
    locked_until            TIMESTAMP,
    last_login_at           TIMESTAMP,
    last_login_ip           TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP,
    CONSTRAINT chk_base_currency CHECK (
        LENGTH(base_currency) >= 3 AND LENGTH(base_currency) <= 10
        AND base_currency = UPPER(base_currency)
        AND base_currency ~ '^[A-Z]+$'
    )
);

CREATE INDEX idx_users_base_currency ON users(base_currency);

-- ── 4. user_settings ────────────────────────────────────────────────────────
CREATE TABLE user_settings (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE,
    theme       VARCHAR(10) NOT NULL DEFAULT 'dark',
    date_format VARCHAR(20) NOT NULL DEFAULT 'MM/DD/YYYY',
    number_format VARCHAR(20) NOT NULL DEFAULT '1,234.56',
    language    VARCHAR(10) NOT NULL DEFAULT 'en',
    timezone    VARCHAR(50) NOT NULL DEFAULT 'UTC',
    country     VARCHAR(2) NOT NULL DEFAULT 'FR',
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CHECK (theme IN ('dark', 'light')),
    CHECK (date_format IN ('MM/DD/YYYY', 'DD/MM/YYYY', 'YYYY-MM-DD')),
    CHECK (number_format IN ('1,234.56', '1.234,56', '1 234,56')),
    CHECK (language IN ('en', 'fr', 'es', 'de', 'it', 'pt', 'ja', 'zh', 'ko', 'ar', 'ru')),
    CHECK (LENGTH(timezone) >= 3 AND LENGTH(timezone) <= 50)
);

CREATE INDEX idx_user_settings_user_id ON user_settings(user_id);

-- ── 5. institutions ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS institutions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    bic         VARCHAR(11),
    country     CHAR(2),
    logo        TEXT,
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE INDEX idx_institution_country   ON institutions(country);
CREATE INDEX idx_institution_is_system ON institutions(is_system);
CREATE INDEX idx_institution_name      ON institutions(name);

-- ── 6. accounts ─────────────────────────────────────────────────────────────
CREATE TABLE accounts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    name                VARCHAR(500) NOT NULL,
    account_type        VARCHAR(20) NOT NULL,
    currency            CHAR(3) NOT NULL,
    balance             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    description         TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    opening_balance     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    opening_date        DATE NOT NULL DEFAULT '2026-01-01',
    institution_id      BIGINT,
    account_number      VARCHAR(512),
    is_interest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    interest_period     VARCHAR(20),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_currency      CHECK (LENGTH(currency) = 3),
    CONSTRAINT chk_account_type  CHECK (account_type IN ('CHECKING', 'SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'CASH', 'OTHER'))
);

CREATE INDEX idx_account_user_id     ON accounts(user_id);
CREATE INDEX idx_account_type        ON accounts(account_type);
CREATE INDEX idx_account_is_active   ON accounts(is_active);
CREATE INDEX idx_account_user_active ON accounts(user_id, is_active);
CREATE INDEX idx_account_user_type   ON accounts(user_id, account_type);
CREATE INDEX idx_account_institution ON accounts(institution_id);
CREATE INDEX idx_account_number      ON accounts(account_number);
CREATE INDEX idx_account_user_number ON accounts(user_id, account_number);

-- ── 7. categories ───────────────────────────────────────────────────────────
CREATE TABLE categories (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    name            TEXT NOT NULL,
    category_type   TEXT NOT NULL CHECK (category_type IN ('INCOME', 'EXPENSE')),
    parent_id       BIGINT,
    icon            TEXT,
    color           TEXT,
    is_system       BOOLEAN NOT NULL DEFAULT FALSE,
    mcc_code        VARCHAR(10),
    name_key        VARCHAR(100),
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE CASCADE
);

CREATE INDEX idx_category_mcc_code ON categories(mcc_code);

-- ── 8. transactions ─────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    account_id          BIGINT NOT NULL,
    to_account_id       BIGINT,
    transaction_type    TEXT NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount              NUMERIC(19, 4) NOT NULL,
    currency            TEXT NOT NULL,
    category_id         BIGINT,
    transaction_date    DATE NOT NULL,
    description         TEXT,
    notes               TEXT,
    tags                TEXT,
    payee               TEXT,
    transfer_id         VARCHAR(36),
    payment_method      VARCHAR(20),
    liability_id        BIGINT,
    external_reference  VARCHAR(255),
    is_reconciled       BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP,
    FOREIGN KEY (user_id)       REFERENCES users(id)        ON DELETE CASCADE,
    FOREIGN KEY (account_id)    REFERENCES accounts(id)     ON DELETE RESTRICT,
    FOREIGN KEY (to_account_id) REFERENCES accounts(id)     ON DELETE RESTRICT,
    FOREIGN KEY (category_id)   REFERENCES categories(id)   ON DELETE SET NULL,
    FOREIGN KEY (liability_id)  REFERENCES liabilities(id)  ON DELETE SET NULL,
    CHECK (amount > 0),
    CHECK (LENGTH(currency) = 3)
);

CREATE INDEX idx_transaction_user_id        ON transactions(user_id);
CREATE INDEX idx_transaction_account_id     ON transactions(account_id);
CREATE INDEX idx_transaction_category_id    ON transactions(category_id);
CREATE INDEX idx_transaction_date           ON transactions(transaction_date);
CREATE INDEX idx_transaction_type           ON transactions(transaction_type);
CREATE INDEX idx_transaction_transfer_id    ON transactions(transfer_id);
CREATE INDEX idx_transaction_payment_method ON transactions(payment_method);
CREATE INDEX idx_transaction_liability_id   ON transactions(liability_id);
CREATE INDEX idx_transaction_external_reference
    ON transactions(account_id, external_reference) WHERE external_reference IS NOT NULL;
CREATE INDEX idx_transaction_user_deleted_date
    ON transactions(user_id, is_deleted, transaction_date DESC);
CREATE INDEX idx_transaction_account_deleted
    ON transactions(account_id, is_deleted);

-- ── 9. net_worth ────────────────────────────────────────────────────────────
CREATE TABLE net_worth (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    snapshot_date       DATE NOT NULL,
    total_assets        DECIMAL(19, 2) NOT NULL,
    total_liabilities   DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    net_worth           DECIMAL(19, 2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'EUR',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_net_worth_user_date ON net_worth(user_id, snapshot_date);
CREATE INDEX idx_net_worth_snapshot_date     ON net_worth(snapshot_date);
CREATE INDEX idx_net_worth_user_date_desc    ON net_worth(user_id, snapshot_date DESC);

-- ── 10. assets ──────────────────────────────────────────────────────────────
CREATE TABLE assets (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    account_id          BIGINT,
    name                VARCHAR(500) NOT NULL,
    asset_type          VARCHAR(20) NOT NULL,
    symbol              VARCHAR(20),
    quantity            DECIMAL(19, 8) NOT NULL,
    purchase_price      DECIMAL(19, 4) NOT NULL,
    current_price       DECIMAL(19, 4) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    purchase_date       DATE NOT NULL,
    notes               TEXT,
    last_updated        TIMESTAMP,
    serial_number       VARCHAR(500),
    brand               VARCHAR(500),
    model               VARCHAR(500),
    condition           VARCHAR(20),
    warranty_expiration DATE,
    useful_life_years   INTEGER,
    photo_path          VARCHAR(500),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    CONSTRAINT fk_assets_user     FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_assets_account  FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT chk_asset_quantity_positive          CHECK (quantity > 0),
    CONSTRAINT chk_asset_purchase_price_non_negative CHECK (purchase_price >= 0),
    CONSTRAINT chk_asset_current_price_non_negative  CHECK (current_price >= 0),
    CONSTRAINT chk_asset_currency_length             CHECK (LENGTH(currency) = 3),
    CONSTRAINT chk_asset_type_valid CHECK (asset_type IN (
        'STOCK', 'ETF', 'MUTUAL_FUND', 'BOND', 'CRYPTO', 'COMMODITY',
        'REAL_ESTATE', 'VEHICLE', 'JEWELRY', 'COLLECTIBLE', 'ELECTRONICS',
        'FURNITURE', 'OTHER'
    ))
);

CREATE INDEX idx_asset_user_id      ON assets(user_id);
CREATE INDEX idx_asset_account_id   ON assets(account_id);
CREATE INDEX idx_asset_type         ON assets(asset_type);
CREATE INDEX idx_asset_symbol       ON assets(symbol);
CREATE INDEX idx_asset_condition    ON assets(condition);
CREATE INDEX idx_asset_warranty_expiration ON assets(warranty_expiration);
CREATE INDEX idx_asset_user_currency ON assets(user_id, currency);

-- ── 11. liabilities ─────────────────────────────────────────────────────────
CREATE TABLE liabilities (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              BIGINT NOT NULL,
    name                 VARCHAR(512) NOT NULL,
    type                 VARCHAR(20) NOT NULL,
    principal            VARCHAR(512) NOT NULL,
    current_balance      VARCHAR(512) NOT NULL,
    interest_rate        VARCHAR(512),
    start_date           DATE NOT NULL,
    end_date             DATE,
    minimum_payment      VARCHAR(512),
    currency             VARCHAR(3) NOT NULL,
    notes                TEXT,
    institution_id       BIGINT REFERENCES institutions(id),
    insurance_percentage VARCHAR(512),
    additional_fees      VARCHAR(512),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_liabilities_user          FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_liability_currency_length CHECK (LENGTH(currency) = 3),
    CONSTRAINT chk_liability_type_valid      CHECK (type IN ('LOAN', 'MORTGAGE', 'CREDIT_CARD', 'PERSONAL_LOAN', 'OTHER')),
    CONSTRAINT chk_liability_dates_logical   CHECK (end_date IS NULL OR end_date >= start_date)
);

-- ── 12. currencies ──────────────────────────────────────────────────────────
CREATE TABLE currencies (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(10) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    symbol      VARCHAR(10) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    name_key    VARCHAR(100),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_currency_code UNIQUE (code),
    CONSTRAINT chk_currency_code_format CHECK (
        LENGTH(code) >= 3 AND LENGTH(code) <= 10
        AND code = UPPER(code)
        AND code ~ '^[A-Z]+$'
    )
);

CREATE INDEX idx_currency_active ON currencies(is_active);

-- ── 13. exchange_rates ──────────────────────────────────────────────────────
CREATE TABLE exchange_rates (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    base_currency   VARCHAR(10) NOT NULL,
    target_currency VARCHAR(10) NOT NULL,
    rate            DECIMAL(18, 8) NOT NULL,
    rate_date       DATE NOT NULL,
    source          VARCHAR(100) DEFAULT 'system',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exchange_rate_currencies_date
        UNIQUE (base_currency, target_currency, rate_date),
    CONSTRAINT chk_exchange_rate_positive CHECK (rate > 0),
    CONSTRAINT chk_base_currency_format CHECK (
        LENGTH(base_currency) >= 3 AND base_currency = UPPER(base_currency)
        AND base_currency ~ '^[A-Z]+$'
    ),
    CONSTRAINT chk_target_currency_format CHECK (
        LENGTH(target_currency) >= 3 AND target_currency = UPPER(target_currency)
        AND target_currency ~ '^[A-Z]+$'
    )
);

CREATE INDEX idx_exchange_rate_base           ON exchange_rates(base_currency);
CREATE INDEX idx_exchange_rate_target         ON exchange_rates(target_currency);
CREATE INDEX idx_exchange_rate_date           ON exchange_rates(rate_date);
CREATE INDEX idx_exchange_rate_currencies_date ON exchange_rates(base_currency, target_currency, rate_date DESC);

-- ── 14. payees ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payees (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(512) NOT NULL,
    logo        TEXT,
    category    VARCHAR(50),
    category_id BIGINT REFERENCES categories(id),
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE INDEX idx_payee_category_id ON payees(category_id);

-- ── 15. budgets ─────────────────────────────────────────────────────────────
CREATE TABLE budgets (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    amount      VARCHAR(512) NOT NULL,
    period      VARCHAR(20) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    rollover    BOOLEAN NOT NULL DEFAULT FALSE,
    notes       VARCHAR(500),
    currency    VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budget_user     FOREIGN KEY (user_id)     REFERENCES users(id)      ON DELETE CASCADE,
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,
    CONSTRAINT chk_budget_dates  CHECK (end_date >= start_date),
    CONSTRAINT chk_budget_period CHECK (period IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT chk_budget_currency CHECK (
        LENGTH(currency) >= 3 AND LENGTH(currency) <= 10
        AND currency = UPPER(currency)
        AND currency ~ '^[A-Z]+$'
    )
);

CREATE INDEX idx_budget_user_period_start ON budgets(user_id, period, start_date);

-- ── 16. budget_alerts ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS budget_alerts (
    id              VARCHAR(36) PRIMARY KEY,
    budget_id       BIGINT NOT NULL,
    threshold       DECIMAL(5, 2) NOT NULL CHECK (threshold >= 1.00 AND threshold <= 150.00),
    is_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    last_triggered  TIMESTAMP,
    is_read         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_budget    FOREIGN KEY (budget_id) REFERENCES budgets(id) ON DELETE CASCADE,
    CONSTRAINT uk_budget_threshold UNIQUE (budget_id, threshold)
);

CREATE INDEX idx_alert_budget_id      ON budget_alerts(budget_id);
CREATE INDEX idx_alert_enabled        ON budget_alerts(is_enabled) WHERE is_enabled = TRUE;
CREATE INDEX idx_alert_unread         ON budget_alerts(is_read) WHERE is_read = FALSE;
CREATE INDEX idx_alert_last_triggered ON budget_alerts(last_triggered) WHERE last_triggered IS NOT NULL;

-- ── 17. real_estate_properties ──────────────────────────────────────────────
CREATE TABLE real_estate_properties (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    name            VARCHAR(500) NOT NULL,
    address         VARCHAR(1000) NOT NULL,
    property_type   VARCHAR(20) NOT NULL,
    purchase_price  VARCHAR(500) NOT NULL,
    purchase_date   DATE NOT NULL,
    current_value   VARCHAR(500) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    mortgage_id     BIGINT,
    rental_income   VARCHAR(500),
    notes           TEXT,
    documents       TEXT,
    latitude        DECIMAL(10, 7),
    longitude       DECIMAL(10, 7),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    asset_id        BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_real_estate_user      FOREIGN KEY (user_id)     REFERENCES users(id)       ON DELETE CASCADE,
    CONSTRAINT fk_real_estate_mortgage  FOREIGN KEY (mortgage_id) REFERENCES liabilities(id) ON DELETE SET NULL,
    CONSTRAINT fk_real_estate_asset     FOREIGN KEY (asset_id)    REFERENCES assets(id)      ON DELETE SET NULL,
    CONSTRAINT chk_real_estate_currency_length    CHECK (LENGTH(currency) = 3),
    CONSTRAINT chk_real_estate_property_type_valid CHECK (property_type IN ('RESIDENTIAL', 'COMMERCIAL', 'LAND', 'MIXED_USE', 'INDUSTRIAL', 'OTHER')),
    CONSTRAINT chk_real_estate_latitude_range     CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT chk_real_estate_longitude_range    CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
);

-- ── 18. ai_conversations ────────────────────────────────────────────────────
CREATE TABLE ai_conversations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    messages    TEXT NOT NULL,
    title       VARCHAR(200),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_conversation_user_id    ON ai_conversations(user_id);
CREATE INDEX idx_ai_conversation_created_at ON ai_conversations(created_at DESC);
CREATE INDEX idx_ai_conversation_updated_at ON ai_conversations(updated_at DESC);

-- ── 19. insights ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS insights (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(50) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority    VARCHAR(20) NOT NULL,
    dismissed   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_insight_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_insight_type CHECK (type IN (
        'SPENDING_ANOMALY', 'BUDGET_WARNING', 'BUDGET_RECOMMENDATION',
        'SAVINGS_OPPORTUNITY', 'INVESTMENT_SUGGESTION', 'DEBT_ALERT',
        'CASH_FLOW_WARNING', 'TAX_OPTIMIZATION', 'GOAL_PROGRESS',
        'GENERAL_TIP', 'UNUSUAL_TRANSACTION', 'REGION_COMPARISON',
        'TAX_OBLIGATION', 'RECURRING_BILLING'
    )),
    CONSTRAINT chk_insight_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_insight_user_id     ON insights(user_id);
CREATE INDEX idx_insight_created_at  ON insights(created_at DESC);
CREATE INDEX idx_insight_priority    ON insights(priority);
CREATE INDEX idx_insight_dismissed   ON insights(dismissed);
CREATE INDEX idx_insight_user_active ON insights(user_id, dismissed, priority, created_at DESC);

-- ── 20. attachments ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS attachments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    entity_type VARCHAR(20) NOT NULL,
    entity_id   BIGINT NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_type   VARCHAR(100) NOT NULL,
    file_size   BIGINT NOT NULL,
    file_path   VARCHAR(500) NOT NULL UNIQUE,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(500),
    CONSTRAINT fk_attachment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_attachment_entity_type CHECK (entity_type IN (
        'TRANSACTION', 'ASSET', 'REAL_ESTATE', 'LIABILITY', 'ACCOUNT'
    )),
    CONSTRAINT chk_attachment_file_size CHECK (file_size > 0),
    CONSTRAINT chk_attachment_entity_id CHECK (entity_id > 0)
);

CREATE INDEX idx_attachment_user_id          ON attachments(user_id);
CREATE INDEX idx_attachment_entity           ON attachments(entity_type, entity_id);
CREATE INDEX idx_attachment_uploaded_at      ON attachments(uploaded_at DESC);
CREATE INDEX idx_attachment_user_entity_type ON attachments(user_id, entity_type);
CREATE INDEX idx_attachment_user_entity_date ON attachments(user_id, entity_type, entity_id, uploaded_at DESC);
CREATE INDEX idx_attachment_file_type        ON attachments(file_type);

-- ── 21. recurring_transactions ──────────────────────────────────────────────
CREATE TABLE recurring_transactions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    account_id          BIGINT NOT NULL,
    to_account_id       BIGINT,
    transaction_type    TEXT NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount              NUMERIC(19, 4) NOT NULL,
    currency            TEXT NOT NULL,
    category_id         BIGINT,
    description         TEXT NOT NULL,
    notes               TEXT,
    frequency           TEXT NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    next_occurrence     DATE NOT NULL,
    end_date            DATE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id)       REFERENCES users(id)      ON DELETE CASCADE,
    FOREIGN KEY (account_id)    REFERENCES accounts(id)   ON DELETE CASCADE,
    FOREIGN KEY (to_account_id) REFERENCES accounts(id)   ON DELETE CASCADE,
    FOREIGN KEY (category_id)   REFERENCES categories(id) ON DELETE SET NULL,
    CHECK (amount > 0),
    CHECK (LENGTH(currency) = 3)
);

CREATE INDEX idx_recurring_user_active_next ON recurring_transactions(user_id, is_active, next_occurrence);

-- ── 22. Full-text search (PostgreSQL tsvector) ──────────────────────────────
-- Replaces SQLite FTS5 virtual table with PostgreSQL native full-text search.
CREATE TABLE transactions_fts (
    transaction_id  BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    search_vector   tsvector
);

CREATE INDEX idx_transactions_fts_search ON transactions_fts USING gin(search_vector);
CREATE INDEX idx_transactions_fts_user   ON transactions_fts(user_id);

-- ── 23. backups ─────────────────────────────────────────────────────────────
CREATE TABLE backups (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    filename      VARCHAR(255) NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    file_size     BIGINT NOT NULL,
    checksum      VARCHAR(64) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    backup_type   VARCHAR(20) NOT NULL DEFAULT 'MANUAL' CHECK (backup_type IN ('MANUAL', 'AUTOMATIC')),
    description   VARCHAR(500),
    error_message VARCHAR(1000),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ── 24. real_estate_simulations ─────────────────────────────────────────────
CREATE TABLE real_estate_simulations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    simulation_type VARCHAR(20) NOT NULL,
    data            TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_simulation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_simulation_type_valid     CHECK (simulation_type IN ('buy_rent', 'rental_investment')),
    CONSTRAINT chk_simulation_name_not_empty CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_simulation_data_not_empty CHECK (LENGTH(TRIM(data)) > 0)
);

-- ── 25. import_sessions ─────────────────────────────────────────────────────
CREATE TABLE import_sessions (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    upload_id            VARCHAR(36) NOT NULL,
    user_id              BIGINT NOT NULL,
    file_name            VARCHAR(255) NOT NULL,
    file_format          VARCHAR(10),
    account_id           BIGINT,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_transactions   INTEGER NOT NULL DEFAULT 0,
    imported_count       INTEGER NOT NULL DEFAULT 0,
    error_count          INTEGER NOT NULL DEFAULT 0,
    duplicate_count      INTEGER NOT NULL DEFAULT 0,
    skipped_count        INTEGER NOT NULL DEFAULT 0,
    error_message        VARCHAR(1000),
    metadata             TEXT,
    suggested_account_name VARCHAR(255),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMP,
    CONSTRAINT fk_import_session_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_import_session_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT chk_import_session_status CHECK (status IN (
        'PENDING', 'PARSING', 'PARSED', 'REVIEWING', 'IMPORTING',
        'COMPLETED', 'CANCELLED', 'FAILED'
    )),
    CONSTRAINT chk_import_session_counts CHECK (
        total_transactions >= 0 AND imported_count >= 0 AND
        error_count >= 0 AND duplicate_count >= 0 AND skipped_count >= 0
    )
);

CREATE INDEX idx_import_session_user    ON import_sessions(user_id);
CREATE INDEX idx_import_session_upload  ON import_sessions(upload_id);
CREATE INDEX idx_import_session_status  ON import_sessions(status);
CREATE INDEX idx_import_session_created ON import_sessions(created_at);

-- PostgreSQL trigger to auto-update updated_at
CREATE OR REPLACE FUNCTION update_import_session_timestamp() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_import_session_timestamp
    BEFORE UPDATE ON import_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_import_session_timestamp();

-- ── 26. interest_rate_variations ────────────────────────────────────────────
CREATE TABLE interest_rate_variations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT NOT NULL,
    rate        DECIMAL(10, 4) NOT NULL,
    tax_rate    DECIMAL(10, 4) DEFAULT 0.0000,
    valid_from  DATE NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX idx_interest_variation_account ON interest_rate_variations(account_id);

-- ── 27. transaction_splits ──────────────────────────────────────────────────
CREATE TABLE transaction_splits (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id  BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    category_id     BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    amount          DECIMAL(19, 4) NOT NULL,
    description     VARCHAR(2000),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_transaction_splits_transaction_id ON transaction_splits(transaction_id);

-- ── 28. transaction_rules ───────────────────────────────────────────────────
CREATE TABLE transaction_rules (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    priority    INTEGER NOT NULL DEFAULT 0,
    is_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_rules_user_priority
    ON transaction_rules(user_id, is_enabled, priority);

CREATE TABLE transaction_rule_conditions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id     BIGINT NOT NULL REFERENCES transaction_rules(id) ON DELETE CASCADE,
    field       TEXT NOT NULL,
    operator    TEXT NOT NULL,
    value       TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_transaction_rule_conditions_rule ON transaction_rule_conditions(rule_id);

CREATE TABLE transaction_rule_actions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id         BIGINT NOT NULL REFERENCES transaction_rules(id) ON DELETE CASCADE,
    action_type     TEXT NOT NULL,
    action_value    TEXT,
    action_value2   TEXT,
    action_value3   TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_transaction_rule_actions_rule ON transaction_rule_actions(rule_id);

-- ── 29. transactions_archive ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transactions_archive (
    id                  BIGINT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    account_id          BIGINT NOT NULL,
    to_account_id       BIGINT,
    transaction_type    TEXT NOT NULL,
    amount              NUMERIC(19, 4) NOT NULL,
    currency            TEXT NOT NULL,
    category_id         BIGINT,
    transaction_date    DATE NOT NULL,
    description         TEXT,
    notes               TEXT,
    tags                TEXT,
    payee               TEXT,
    transfer_id         VARCHAR(36),
    payment_method      VARCHAR(20),
    liability_id        BIGINT,
    external_reference  VARCHAR(255),
    is_reconciled       BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP,
    archived_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_archive_transaction_type CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER'))
);

-- ── 30. security_audit_log ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS security_audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    username    TEXT NOT NULL,
    event_type  TEXT NOT NULL CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT',
        'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED',
        'PASSWORD_CHANGED', 'MASTER_PASSWORD_CHANGED',
        'REGISTRATION', 'UNAUTHORIZED_ACCESS'
    )),
    ip_address  TEXT NOT NULL,
    user_agent  TEXT,
    details     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- Seed data: Currencies (ISO 4217 + Crypto)
-- =============================================================================

INSERT INTO currencies (code, name, symbol, is_active) VALUES
    ('ADP', 'Andorran Peseta', 'ADP', TRUE),
    ('AED', 'United Arab Emirates Dirham', 'AED', TRUE),
    ('AFA', 'Afghan Afghani (1927–2002)', 'AFA', TRUE),
    ('AFN', 'Afghan Afghani', 'AFN', TRUE),
    ('ALL', 'Albanian Lek', 'ALL', TRUE),
    ('AMD', 'Armenian Dram', 'AMD', TRUE),
    ('ANG', 'Netherlands Antillean Guilder', 'ANG', TRUE),
    ('AOA', 'Angolan Kwanza', 'AOA', TRUE),
    ('ARS', 'Argentine Peso', 'ARS', TRUE),
    ('ATS', 'Austrian Schilling', 'ATS', TRUE),
    ('AUD', 'Australian Dollar', 'A$', TRUE),
    ('AWG', 'Aruban Florin', 'AWG', TRUE),
    ('AYM', 'AYM', 'AYM', TRUE),
    ('AZM', 'Azerbaijani Manat (1993–2006)', 'AZM', TRUE),
    ('AZN', 'Azerbaijani Manat', 'AZN', TRUE),
    ('BAM', 'Bosnia-Herzegovina Convertible Mark', 'BAM', TRUE),
    ('BBD', 'Barbadian Dollar', 'BBD', TRUE),
    ('BDT', 'Bangladeshi Taka', 'BDT', TRUE),
    ('BEF', 'Belgian Franc', 'BEF', TRUE),
    ('BGL', 'Bulgarian Hard Lev', 'BGL', TRUE),
    ('BGN', 'Bulgarian Lev', 'BGN', TRUE),
    ('BHD', 'Bahraini Dinar', 'BHD', TRUE),
    ('BIF', 'Burundian Franc', 'BIF', TRUE),
    ('BMD', 'Bermudan Dollar', 'BMD', TRUE),
    ('BND', 'Brunei Dollar', 'BND', TRUE),
    ('BOB', 'Bolivian Boliviano', 'BOB', TRUE),
    ('BOV', 'Bolivian Mvdol', 'BOV', TRUE),
    ('BRL', 'Brazilian Real', 'R$', TRUE),
    ('BSD', 'Bahamian Dollar', 'BSD', TRUE),
    ('BTN', 'Bhutanese Ngultrum', 'BTN', TRUE),
    ('BWP', 'Botswanan Pula', 'BWP', TRUE),
    ('BYB', 'Belarusian Ruble (1994–1999)', 'BYB', TRUE),
    ('BYN', 'Belarusian Ruble', 'BYN', TRUE),
    ('BYR', 'Belarusian Ruble (2000–2016)', 'BYR', TRUE),
    ('BZD', 'Belize Dollar', 'BZD', TRUE),
    ('CAD', 'Canadian Dollar', 'CA$', TRUE),
    ('CDF', 'Congolese Franc', 'CDF', TRUE),
    ('CHE', 'WIR Euro', 'CHE', TRUE),
    ('CHF', 'Swiss Franc', 'CHF', TRUE),
    ('CHW', 'WIR Franc', 'CHW', TRUE),
    ('CLF', 'Chilean Unit of Account (UF)', 'CLF', TRUE),
    ('CLP', 'Chilean Peso', 'CLP', TRUE),
    ('CNY', 'Chinese Yuan', 'CN¥', TRUE),
    ('COP', 'Colombian Peso', 'COP', TRUE),
    ('COU', 'Colombian Real Value Unit', 'COU', TRUE),
    ('CRC', 'Costa Rican Colón', 'CRC', TRUE),
    ('CSD', 'Serbian Dinar (2002–2006)', 'CSD', TRUE),
    ('CUC', 'Cuban Convertible Peso', 'CUC', TRUE),
    ('CUP', 'Cuban Peso', 'CUP', TRUE),
    ('CVE', 'Cape Verdean Escudo', 'CVE', TRUE),
    ('CYP', 'Cypriot Pound', 'CYP', TRUE),
    ('CZK', 'Czech Koruna', 'CZK', TRUE),
    ('DEM', 'German Mark', 'DEM', TRUE),
    ('DJF', 'Djiboutian Franc', 'DJF', TRUE),
    ('DKK', 'Danish Krone', 'DKK', TRUE),
    ('DOP', 'Dominican Peso', 'DOP', TRUE),
    ('DZD', 'Algerian Dinar', 'DZD', TRUE),
    ('EEK', 'Estonian Kroon', 'EEK', TRUE),
    ('EGP', 'Egyptian Pound', 'EGP', TRUE),
    ('ERN', 'Eritrean Nakfa', 'ERN', TRUE),
    ('ESP', 'Spanish Peseta', 'ESP', TRUE),
    ('ETB', 'Ethiopian Birr', 'ETB', TRUE),
    ('EUR', 'Euro', '€', TRUE),
    ('FIM', 'Finnish Markka', 'FIM', TRUE),
    ('FJD', 'Fijian Dollar', 'FJD', TRUE),
    ('FKP', 'Falkland Islands Pound', 'FKP', TRUE),
    ('FRF', 'French Franc', 'FRF', TRUE),
    ('GBP', 'British Pound', '£', TRUE),
    ('GEL', 'Georgian Lari', 'GEL', TRUE),
    ('GHC', 'Ghanaian Cedi (1979–2007)', 'GHC', TRUE),
    ('GHS', 'Ghanaian Cedi', 'GHS', TRUE),
    ('GIP', 'Gibraltar Pound', 'GIP', TRUE),
    ('GMD', 'Gambian Dalasi', 'GMD', TRUE),
    ('GNF', 'Guinean Franc', 'GNF', TRUE),
    ('GRD', 'Greek Drachma', 'GRD', TRUE),
    ('GTQ', 'Guatemalan Quetzal', 'GTQ', TRUE),
    ('GWP', 'Guinea-Bissau Peso', 'GWP', TRUE),
    ('GYD', 'Guyanaese Dollar', 'GYD', TRUE),
    ('HKD', 'Hong Kong Dollar', 'HK$', TRUE),
    ('HNL', 'Honduran Lempira', 'HNL', TRUE),
    ('HRK', 'Croatian Kuna', 'HRK', TRUE),
    ('HTG', 'Haitian Gourde', 'HTG', TRUE),
    ('HUF', 'Hungarian Forint', 'HUF', TRUE),
    ('IDR', 'Indonesian Rupiah', 'IDR', TRUE),
    ('IEP', 'Irish Pound', 'IEP', TRUE),
    ('ILS', 'Israeli New Shekel', '₪', TRUE),
    ('INR', 'Indian Rupee', '₹', TRUE),
    ('IQD', 'Iraqi Dinar', 'IQD', TRUE),
    ('IRR', 'Iranian Rial', 'IRR', TRUE),
    ('ISK', 'Icelandic Króna', 'ISK', TRUE),
    ('ITL', 'Italian Lira', 'ITL', TRUE),
    ('JMD', 'Jamaican Dollar', 'JMD', TRUE),
    ('JOD', 'Jordanian Dinar', 'JOD', TRUE),
    ('JPY', 'Japanese Yen', '¥', TRUE),
    ('KES', 'Kenyan Shilling', 'KES', TRUE),
    ('KGS', 'Kyrgystani Som', 'KGS', TRUE),
    ('KHR', 'Cambodian Riel', 'KHR', TRUE),
    ('KMF', 'Comorian Franc', 'KMF', TRUE),
    ('KPW', 'North Korean Won', 'KPW', TRUE),
    ('KRW', 'South Korean Won', '₩', TRUE),
    ('KWD', 'Kuwaiti Dinar', 'KWD', TRUE),
    ('KYD', 'Cayman Islands Dollar', 'KYD', TRUE),
    ('KZT', 'Kazakhstani Tenge', 'KZT', TRUE),
    ('LAK', 'Laotian Kip', 'LAK', TRUE),
    ('LBP', 'Lebanese Pound', 'LBP', TRUE),
    ('LKR', 'Sri Lankan Rupee', 'LKR', TRUE),
    ('LRD', 'Liberian Dollar', 'LRD', TRUE),
    ('LSL', 'Lesotho Loti', 'LSL', TRUE),
    ('LTL', 'Lithuanian Litas', 'LTL', TRUE),
    ('LUF', 'Luxembourgian Franc', 'LUF', TRUE),
    ('LVL', 'Latvian Lats', 'LVL', TRUE),
    ('LYD', 'Libyan Dinar', 'LYD', TRUE),
    ('MAD', 'Moroccan Dirham', 'MAD', TRUE),
    ('MDL', 'Moldovan Leu', 'MDL', TRUE),
    ('MGA', 'Malagasy Ariary', 'MGA', TRUE),
    ('MGF', 'Malagasy Franc', 'MGF', TRUE),
    ('MKD', 'Macedonian Denar', 'MKD', TRUE),
    ('MMK', 'Myanmar Kyat', 'MMK', TRUE),
    ('MNT', 'Mongolian Tugrik', 'MNT', TRUE),
    ('MOP', 'Macanese Pataca', 'MOP', TRUE),
    ('MRO', 'Mauritanian Ouguiya (1973–2017)', 'MRO', TRUE),
    ('MRU', 'Mauritanian Ouguiya', 'MRU', TRUE),
    ('MTL', 'Maltese Lira', 'MTL', TRUE),
    ('MUR', 'Mauritian Rupee', 'MUR', TRUE),
    ('MVR', 'Maldivian Rufiyaa', 'MVR', TRUE),
    ('MWK', 'Malawian Kwacha', 'MWK', TRUE),
    ('MXN', 'Mexican Peso', 'MX$', TRUE),
    ('MXV', 'Mexican Investment Unit', 'MXV', TRUE),
    ('MYR', 'Malaysian Ringgit', 'MYR', TRUE),
    ('MZM', 'Mozambican Metical (1980–2006)', 'MZM', TRUE),
    ('MZN', 'Mozambican Metical', 'MZN', TRUE),
    ('NAD', 'Namibian Dollar', 'NAD', TRUE),
    ('NGN', 'Nigerian Naira', 'NGN', TRUE),
    ('NIO', 'Nicaraguan Córdoba', 'NIO', TRUE),
    ('NLG', 'Dutch Guilder', 'NLG', TRUE),
    ('NOK', 'Norwegian Krone', 'NOK', TRUE),
    ('NPR', 'Nepalese Rupee', 'NPR', TRUE),
    ('NZD', 'New Zealand Dollar', 'NZ$', TRUE),
    ('OMR', 'Omani Rial', 'OMR', TRUE),
    ('PAB', 'Panamanian Balboa', 'PAB', TRUE),
    ('PEN', 'Peruvian Sol', 'PEN', TRUE),
    ('PGK', 'Papua New Guinean Kina', 'PGK', TRUE),
    ('PHP', 'Philippine Peso', '₱', TRUE),
    ('PKR', 'Pakistani Rupee', 'PKR', TRUE),
    ('PLN', 'Polish Zloty', 'PLN', TRUE),
    ('PTE', 'Portuguese Escudo', 'PTE', TRUE),
    ('PYG', 'Paraguayan Guarani', 'PYG', TRUE),
    ('QAR', 'Qatari Riyal', 'QAR', TRUE),
    ('ROL', 'Romanian Leu (1952–2006)', 'ROL', TRUE),
    ('RON', 'Romanian Leu', 'RON', TRUE),
    ('RSD', 'Serbian Dinar', 'RSD', TRUE),
    ('RUB', 'Russian Ruble', 'RUB', TRUE),
    ('RUR', 'Russian Ruble (1991–1998)', 'RUR', TRUE),
    ('RWF', 'Rwandan Franc', 'RWF', TRUE),
    ('SAR', 'Saudi Riyal', 'SAR', TRUE),
    ('SBD', 'Solomon Islands Dollar', 'SBD', TRUE),
    ('SCR', 'Seychellois Rupee', 'SCR', TRUE),
    ('SDD', 'Sudanese Dinar (1992–2007)', 'SDD', TRUE),
    ('SDG', 'Sudanese Pound', 'SDG', TRUE),
    ('SEK', 'Swedish Krona', 'SEK', TRUE),
    ('SGD', 'Singapore Dollar', 'SGD', TRUE),
    ('SHP', 'St. Helena Pound', 'SHP', TRUE),
    ('SIT', 'Slovenian Tolar', 'SIT', TRUE),
    ('SKK', 'Slovak Koruna', 'SKK', TRUE),
    ('SLE', 'Sierra Leonean Leone', 'SLE', TRUE),
    ('SLL', 'Sierra Leonean Leone (1964—2022)', 'SLL', TRUE),
    ('SOS', 'Somali Shilling', 'SOS', TRUE),
    ('SRD', 'Surinamese Dollar', 'SRD', TRUE),
    ('SRG', 'Surinamese Guilder', 'SRG', TRUE),
    ('SSP', 'South Sudanese Pound', 'SSP', TRUE),
    ('STD', 'São Tomé & Príncipe Dobra (1977–2017)', 'STD', TRUE),
    ('STN', 'São Tomé & Príncipe Dobra', 'STN', TRUE),
    ('SVC', 'Salvadoran Colón', 'SVC', TRUE),
    ('SYP', 'Syrian Pound', 'SYP', TRUE),
    ('SZL', 'Swazi Lilangeni', 'SZL', TRUE),
    ('THB', 'Thai Baht', 'THB', TRUE),
    ('TJS', 'Tajikistani Somoni', 'TJS', TRUE),
    ('TMM', 'Turkmenistani Manat (1993–2009)', 'TMM', TRUE),
    ('TMT', 'Turkmenistani Manat', 'TMT', TRUE),
    ('TND', 'Tunisian Dinar', 'TND', TRUE),
    ('TOP', 'Tongan Paʻanga', 'TOP', TRUE),
    ('TPE', 'Timorese Escudo', 'TPE', TRUE),
    ('TRL', 'Turkish Lira (1922–2005)', 'TRL', TRUE),
    ('TRY', 'Turkish Lira', 'TRY', TRUE),
    ('TTD', 'Trinidad & Tobago Dollar', 'TTD', TRUE),
    ('TWD', 'New Taiwan Dollar', 'NT$', TRUE),
    ('TZS', 'Tanzanian Shilling', 'TZS', TRUE),
    ('UAH', 'Ukrainian Hryvnia', 'UAH', TRUE),
    ('UGX', 'Ugandan Shilling', 'UGX', TRUE),
    ('USD', 'US Dollar', '$', TRUE),
    ('USN', 'US Dollar (Next day)', 'USN', TRUE),
    ('USS', 'US Dollar (Same day)', 'USS', TRUE),
    ('UYI', 'Uruguayan Peso (Indexed Units)', 'UYI', TRUE),
    ('UYU', 'Uruguayan Peso', 'UYU', TRUE),
    ('UZS', 'Uzbekistani Som', 'UZS', TRUE),
    ('VEB', 'Venezuelan Bolívar (1871–2008)', 'VEB', TRUE),
    ('VED', 'Bolívar Soberano', 'VED', TRUE),
    ('VEF', 'Venezuelan Bolívar (2008–2018)', 'VEF', TRUE),
    ('VES', 'Venezuelan Bolívar', 'VES', TRUE),
    ('VND', 'Vietnamese Dong', '₫', TRUE),
    ('VUV', 'Vanuatu Vatu', 'VUV', TRUE),
    ('WST', 'Samoan Tala', 'WST', TRUE),
    ('XAD', 'Arab Accounting Dinar', 'XAD', TRUE),
    ('XAF', 'Central African CFA Franc', 'FCFA', TRUE),
    ('XAG', 'Silver', 'XAG', TRUE),
    ('XAU', 'Gold', 'XAU', TRUE),
    ('XBA', 'European Composite Unit', 'XBA', TRUE),
    ('XBB', 'European Monetary Unit', 'XBB', TRUE),
    ('XBC', 'European Unit of Account (XBC)', 'XBC', TRUE),
    ('XBD', 'European Unit of Account (XBD)', 'XBD', TRUE),
    ('XCD', 'East Caribbean Dollar', 'EC$', TRUE),
    ('XCG', 'Caribbean Guilder', 'XCG', TRUE),
    ('XDR', 'Special Drawing Rights', 'XDR', TRUE),
    ('XFO', 'French Gold Franc', 'XFO', TRUE),
    ('XFU', 'French UIC-Franc', 'XFU', TRUE),
    ('XOF', 'West African CFA Franc', 'F CFA', TRUE),
    ('XPD', 'Palladium', 'XPD', TRUE),
    ('XPF', 'CFP Franc', 'CFPF', TRUE),
    ('XPT', 'Platinum', 'XPT', TRUE),
    ('XSU', 'Sucre', 'XSU', TRUE),
    ('XTS', 'Testing Currency Code', 'XTS', TRUE),
    ('XUA', 'ADB Unit of Account', 'XUA', TRUE),
    ('XXX', 'Unknown Currency', '¤', TRUE),
    ('YER', 'Yemeni Rial', 'YER', TRUE),
    ('YUM', 'Yugoslavian New Dinar (1994–2002)', 'YUM', TRUE),
    ('ZAR', 'South African Rand', 'ZAR', TRUE),
    ('ZMK', 'Zambian Kwacha (1968–2012)', 'ZMK', TRUE),
    ('ZMW', 'Zambian Kwacha', 'ZMW', TRUE),
    ('ZWD', 'Zimbabwean Dollar (1980–2008)', 'ZWD', TRUE),
    ('ZWG', 'Zimbabwe Gold', 'ZWG', TRUE),
    ('ZWL', 'Zimbabwean Dollar (2009)', 'ZWL', TRUE),
    ('ZWN', 'ZWN', 'ZWN', TRUE),
    ('ZWR', 'Zimbabwean Dollar (2008)', 'ZWR', TRUE);

-- Cryptocurrencies
INSERT INTO currencies (code, name, symbol, is_active) VALUES
    ('BTC',  'Bitcoin',      '₿',    TRUE),
    ('ETH',  'Ethereum',     'Ξ',    TRUE),
    ('BNB',  'Binance Coin', 'BNB',  TRUE),
    ('XRP',  'Ripple',       'XRP',  TRUE),
    ('ADA',  'Cardano',      'ADA',  TRUE),
    ('SOL',  'Solana',       'SOL',  TRUE),
    ('DOT',  'Polkadot',     'DOT',  TRUE),
    ('DOGE', 'Dogecoin',     'Ð',    TRUE),
    ('USDT', 'Tether',       'USDT', TRUE),
    ('USDC', 'USD Coin',     'USDC', TRUE);

-- Backfill currency name_key values
UPDATE currencies SET name_key = 'currency.' || LOWER(code);

-- =============================================================================
-- End of consolidated PostgreSQL schema
-- =============================================================================
