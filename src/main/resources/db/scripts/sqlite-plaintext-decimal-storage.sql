-- Preserve exact plaintext decimal strings, just as encrypted values are preserved.
-- V95 owns the transaction and temporarily disables foreign keys while rebuilding.
CREATE TEMP TABLE finance_saved_sequences AS SELECT name, seq FROM sqlite_sequence;

CREATE TABLE accounts_decimal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    name VARCHAR(500) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    currency CHAR(3) NOT NULL,
    balance VARCHAR(512) NOT NULL DEFAULT 0,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    opening_balance VARCHAR(512) NOT NULL DEFAULT 0,
    opening_date DATE NOT NULL DEFAULT '2026-01-01'
, institution_id INTEGER, is_interest_enabled BOOLEAN DEFAULT 0 NOT NULL, interest_period VARCHAR(20) DEFAULT NULL, account_number VARCHAR(50), version BIGINT NOT NULL DEFAULT 0);

INSERT INTO accounts_decimal ("id", "user_id", "name", "account_type", "currency", "balance", "description", "is_active", "created_at", "updated_at", "opening_balance", "opening_date", "institution_id", "is_interest_enabled", "interest_period", "account_number", "version") SELECT "id", "user_id", "name", "account_type", "currency", CAST("balance" AS TEXT), "description", "is_active", "created_at", "updated_at", CAST("opening_balance" AS TEXT), "opening_date", "institution_id", "is_interest_enabled", "interest_period", "account_number", "version" FROM accounts;

DROP TABLE accounts;
ALTER TABLE accounts_decimal RENAME TO accounts;

CREATE INDEX idx_account_user_id ON accounts(user_id);

CREATE INDEX idx_account_type ON accounts(account_type);

CREATE INDEX idx_account_is_active ON accounts(is_active);

CREATE INDEX idx_account_user_active ON accounts(user_id, is_active);

CREATE INDEX idx_account_user_type ON accounts(user_id, account_type);

CREATE INDEX idx_account_opening_date ON accounts(opening_date);

CREATE INDEX idx_account_institution ON accounts(institution_id);

CREATE INDEX idx_account_number ON accounts(account_number);

CREATE INDEX idx_account_user_number ON accounts(user_id, account_number);

CREATE TABLE transactions_decimal (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id             INTEGER NOT NULL,
    account_id          INTEGER NOT NULL,                  -- Source account
    to_account_id       INTEGER,                            -- Destination account (for transfers only)
    transaction_type    TEXT NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount VARCHAR(512) NOT NULL,                      -- Positive decimal value
    currency            TEXT NOT NULL,                      -- ISO 4217 currency code (3 chars)
    category_id         INTEGER,                            -- Optional category reference
    transaction_date    TEXT NOT NULL,                      -- ISO 8601 date (YYYY-MM-DD)
    description         TEXT,                               -- Encrypted brief description
    notes               TEXT,                               -- Encrypted detailed notes
    tags                TEXT,                               -- Comma-separated tags
    payee               TEXT,                               -- Payee/payer name
    is_reconciled       INTEGER NOT NULL DEFAULT 0,        -- Reconciliation status (0=false, 1=true)
    is_deleted          INTEGER NOT NULL DEFAULT 0,        -- Soft delete flag (0=false, 1=true)
    created_at          TEXT NOT NULL,                      -- ISO 8601 datetime
    updated_at          TEXT, transfer_id VARCHAR(36), payment_method VARCHAR(20), liability_id INTEGER REFERENCES liabilities(id) ON DELETE SET NULL, external_reference VARCHAR(255), payee_id INTEGER REFERENCES payees(id) ON DELETE SET NULL, currency_id INTEGER REFERENCES currencies(id), original_amount VARCHAR(512), original_currency VARCHAR(3), conversion_rate NUMERIC(18, 8), tranche_id INTEGER REFERENCES liability_tranches(id) ON DELETE SET NULL, real_estate_id INTEGER REFERENCES real_estate_properties(id) ON DELETE SET NULL, asset_id INTEGER REFERENCES assets(id) ON DELETE SET NULL, movement_type VARCHAR(30), account_amount VARCHAR(512), account_currency VARCHAR(3), principal_amount TEXT,                               -- ISO 8601 datetime

    -- Foreign key constraint - ensures transaction belongs to valid user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Foreign key constraint - source account must exist
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,

    -- Foreign key constraint - destination account must exist (for transfers)
    FOREIGN KEY (to_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,

    -- Foreign key constraint - category must exist (optional)
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,

    -- Business rule constraints
    -- Amount may be ciphertext; positivity is validated before encryption by the application.
    CHECK (LENGTH(currency) = 3)                            -- Currency must be 3-letter code
);

INSERT INTO transactions_decimal ("id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "transaction_date", "description", "notes", "tags", "payee", "is_reconciled", "is_deleted", "created_at", "updated_at", "transfer_id", "payment_method", "liability_id", "external_reference", "payee_id", "currency_id", "original_amount", "original_currency", "conversion_rate", "tranche_id", "real_estate_id", "asset_id", "movement_type", "account_amount", "account_currency", "principal_amount") SELECT "id", "user_id", "account_id", "to_account_id", "transaction_type", CAST("amount" AS TEXT), "currency", "category_id", "transaction_date", "description", "notes", "tags", "payee", "is_reconciled", "is_deleted", "created_at", "updated_at", "transfer_id", "payment_method", "liability_id", "external_reference", "payee_id", "currency_id", "original_amount", "original_currency", "conversion_rate", "tranche_id", "real_estate_id", "asset_id", "movement_type", "account_amount", "account_currency", "principal_amount" FROM transactions;

DROP TABLE transactions;
ALTER TABLE transactions_decimal RENAME TO transactions;

CREATE INDEX idx_transaction_user_id ON transactions(user_id);

CREATE INDEX idx_transaction_account_id ON transactions(account_id);

CREATE INDEX idx_transaction_category_id ON transactions(category_id);

CREATE INDEX idx_transaction_date ON transactions(transaction_date);

CREATE INDEX idx_transaction_type ON transactions(transaction_type);

CREATE INDEX idx_transaction_user_date ON transactions(user_id, transaction_date);

CREATE INDEX idx_transaction_account_date ON transactions(account_id, transaction_date);

CREATE INDEX idx_transaction_is_deleted ON transactions(is_deleted);

CREATE INDEX idx_transaction_is_reconciled ON transactions(is_reconciled);

CREATE INDEX idx_transaction_transfer_id ON transactions(transfer_id);

CREATE INDEX idx_transaction_payment_method ON transactions(payment_method);

CREATE INDEX idx_transaction_liability_id ON transactions(liability_id);

CREATE INDEX idx_transaction_external_reference
    ON transactions (account_id, external_reference)
    WHERE external_reference IS NOT NULL;

CREATE INDEX idx_transaction_user_deleted_date
    ON transactions(user_id, is_deleted, transaction_date DESC);

CREATE INDEX idx_transaction_account_deleted
    ON transactions(account_id, is_deleted);

CREATE INDEX idx_transaction_payee_id ON transactions(payee_id);

CREATE INDEX idx_transaction_currency_id ON transactions(currency_id);

CREATE INDEX idx_transaction_tranche_id ON transactions(tranche_id);

CREATE INDEX idx_transaction_real_estate_id ON transactions(real_estate_id);

CREATE INDEX idx_transaction_asset_id ON transactions(asset_id);

CREATE INDEX idx_transaction_movement_type ON transactions(movement_type);

CREATE TABLE transactions_archive_decimal (
    id                  INTEGER PRIMARY KEY,
    user_id             INTEGER NOT NULL,
    account_id          INTEGER NOT NULL,
    to_account_id       INTEGER,
    transaction_type    TEXT NOT NULL CHECK(transaction_type IN ('INCOME','EXPENSE','TRANSFER')),
    amount VARCHAR(512) NOT NULL,
    currency            TEXT NOT NULL,
    category_id         INTEGER,
    transaction_date    TEXT NOT NULL,
    description         TEXT,
    notes               TEXT,
    tags                TEXT,
    payee               TEXT,
    is_reconciled       INTEGER NOT NULL DEFAULT 0,
    is_deleted          INTEGER NOT NULL DEFAULT 0,
    transfer_id         VARCHAR(36),
    payment_method      VARCHAR(20),
    liability_id        INTEGER,
    external_reference  VARCHAR(255),
    created_at          TEXT NOT NULL,
    updated_at          TEXT,
    archived_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
, payee_id INTEGER, currency_id INTEGER, original_amount VARCHAR(512), original_currency VARCHAR(3), conversion_rate NUMERIC(18, 8), tranche_id INTEGER, real_estate_id INTEGER, asset_id INTEGER, movement_type VARCHAR(30), account_amount VARCHAR(512), account_currency VARCHAR(3), principal_amount TEXT);

INSERT INTO transactions_archive_decimal ("id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "transaction_date", "description", "notes", "tags", "payee", "is_reconciled", "is_deleted", "transfer_id", "payment_method", "liability_id", "external_reference", "created_at", "updated_at", "archived_at", "payee_id", "currency_id", "original_amount", "original_currency", "conversion_rate", "tranche_id", "real_estate_id", "asset_id", "movement_type", "account_amount", "account_currency", "principal_amount") SELECT "id", "user_id", "account_id", "to_account_id", "transaction_type", CAST("amount" AS TEXT), "currency", "category_id", "transaction_date", "description", "notes", "tags", "payee", "is_reconciled", "is_deleted", "transfer_id", "payment_method", "liability_id", "external_reference", "created_at", "updated_at", "archived_at", "payee_id", "currency_id", "original_amount", "original_currency", "conversion_rate", "tranche_id", "real_estate_id", "asset_id", "movement_type", "account_amount", "account_currency", "principal_amount" FROM transactions_archive;

DROP TABLE transactions_archive;
ALTER TABLE transactions_archive_decimal RENAME TO transactions_archive;

CREATE INDEX idx_txn_archive_user_date
    ON transactions_archive(user_id, transaction_date DESC);

CREATE INDEX idx_txn_archive_account
    ON transactions_archive(account_id);

CREATE TABLE recurring_transactions_decimal (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id             INTEGER NOT NULL,
    account_id          INTEGER NOT NULL,                  -- Source account
    to_account_id       INTEGER,                            -- Destination account (for transfers only)
    transaction_type    TEXT NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount VARCHAR(512) NOT NULL,                      -- Positive decimal value
    currency            TEXT NOT NULL,                      -- ISO 4217 currency code (3 chars)
    category_id         INTEGER,                            -- Optional category reference
    description         TEXT NOT NULL,                      -- Encrypted brief description (e.g., "Monthly Rent")
    notes               TEXT,                               -- Encrypted detailed notes
    frequency           TEXT NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    next_occurrence     TEXT NOT NULL,                      -- ISO 8601 date (YYYY-MM-DD) - next scheduled date
    end_date            TEXT,                               -- ISO 8601 date - optional end date for recurring
    is_active           INTEGER NOT NULL DEFAULT 1,        -- Active status (0=paused, 1=active)
    created_at          TEXT NOT NULL,                      -- ISO 8601 datetime
    updated_at          TEXT NOT NULL, currency_id INTEGER REFERENCES currencies(id), anchor_day INTEGER NOT NULL DEFAULT 1 CHECK (anchor_day BETWEEN 1 AND 31),                      -- ISO 8601 datetime

    -- Foreign key constraint - ensures recurring transaction belongs to valid user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Foreign key constraint - source account must exist
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,

    -- Foreign key constraint - destination account must exist (for transfers)
    FOREIGN KEY (to_account_id) REFERENCES accounts(id) ON DELETE CASCADE,

    -- Foreign key constraint - category must exist (optional)
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,

    -- Business rule constraints
    -- Amount may be ciphertext; positivity is validated before encryption by the application.
    CHECK (LENGTH(currency) = 3)                            -- Currency must be 3-letter code
);

INSERT INTO recurring_transactions_decimal ("id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "description", "notes", "frequency", "next_occurrence", "end_date", "is_active", "created_at", "updated_at", "currency_id", "anchor_day") SELECT "id", "user_id", "account_id", "to_account_id", "transaction_type", CAST("amount" AS TEXT), "currency", "category_id", "description", "notes", "frequency", "next_occurrence", "end_date", "is_active", "created_at", "updated_at", "currency_id", "anchor_day" FROM recurring_transactions;

DROP TABLE recurring_transactions;
ALTER TABLE recurring_transactions_decimal RENAME TO recurring_transactions;

CREATE INDEX idx_recurring_user_id ON recurring_transactions(user_id);

CREATE INDEX idx_recurring_account_id ON recurring_transactions(account_id);

CREATE INDEX idx_recurring_next_occurrence ON recurring_transactions(next_occurrence);

CREATE INDEX idx_recurring_is_active ON recurring_transactions(is_active);

CREATE INDEX idx_recurring_active_next_occurrence ON recurring_transactions(is_active, next_occurrence);

CREATE INDEX idx_recurring_frequency ON recurring_transactions(frequency);

CREATE INDEX idx_recurring_user_active ON recurring_transactions(user_id, is_active);

CREATE INDEX idx_recurring_user_active_next
    ON recurring_transactions(user_id, is_active, next_occurrence);

CREATE INDEX idx_recurring_transaction_currency_id ON recurring_transactions(currency_id);

UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE((SELECT seq FROM finance_saved_sequences saved WHERE saved.name = sqlite_sequence.name), seq));
DROP TABLE finance_saved_sequences;
