-- V96 owns the transaction and disables foreign keys during these table rebuilds.
-- Preserve all financial values, indexes, triggers, references and AUTOINCREMENT high-water marks.
CREATE TEMP TABLE finance_saved_sequences AS SELECT name, seq FROM sqlite_sequence;

CREATE TABLE accounts_catalog ( id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    name VARCHAR(500) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    balance VARCHAR(512) NOT NULL DEFAULT 0,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    opening_balance VARCHAR(512) NOT NULL DEFAULT 0,
    opening_date DATE NOT NULL DEFAULT '2026-01-01' ,
    institution_id INTEGER,
    is_interest_enabled BOOLEAN DEFAULT 0 NOT NULL,
    interest_period VARCHAR(20) DEFAULT NULL,
    account_number VARCHAR(50),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (LENGTH(currency) BETWEEN 3 AND 10));

INSERT INTO accounts_catalog ("id", "user_id", "name", "account_type", "currency", "balance", "description", "is_active", "created_at", "updated_at", "opening_balance", "opening_date", "institution_id", "is_interest_enabled", "interest_period", "account_number", "version") SELECT "id", "user_id", "name", "account_type", "currency", "balance", "description", "is_active", "created_at", "updated_at", "opening_balance", "opening_date", "institution_id", "is_interest_enabled", "interest_period", "account_number", "version" FROM accounts;

DROP TABLE accounts;

ALTER TABLE accounts_catalog RENAME TO accounts;

CREATE INDEX idx_account_institution ON accounts(institution_id);

CREATE INDEX idx_account_is_active ON accounts(is_active);

CREATE INDEX idx_account_number ON accounts(account_number);

CREATE INDEX idx_account_opening_date ON accounts(opening_date);

CREATE INDEX idx_account_type ON accounts(account_type);

CREATE INDEX idx_account_user_active ON accounts(user_id, is_active);

CREATE INDEX idx_account_user_id ON accounts(user_id);

CREATE INDEX idx_account_user_number ON accounts(user_id, account_number);

CREATE INDEX idx_account_user_type ON accounts(user_id, account_type);

CREATE TABLE transactions_catalog ( id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    account_id INTEGER NOT NULL,
    to_account_id INTEGER,
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('INCOME',
    'EXPENSE',
    'TRANSFER')),
    amount VARCHAR(512) NOT NULL,
    currency TEXT NOT NULL,
    category_id INTEGER,
    transaction_date TEXT NOT NULL,
    description TEXT,
    notes TEXT,
    tags TEXT,
    payee TEXT,
    is_reconciled INTEGER NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT,
    transfer_id VARCHAR(36),
    payment_method VARCHAR(20),
    liability_id INTEGER REFERENCES liabilities(id) ON DELETE SET NULL,
    external_reference VARCHAR(255),
    payee_id INTEGER REFERENCES payees(id) ON DELETE SET NULL,
    currency_id INTEGER REFERENCES currencies(id),
    original_amount VARCHAR(512),
    original_currency VARCHAR(10),
    conversion_rate NUMERIC(18,
    8),
    tranche_id INTEGER REFERENCES liability_tranches(id) ON DELETE SET NULL,
    real_estate_id INTEGER REFERENCES real_estate_properties(id) ON DELETE SET NULL,
    asset_id INTEGER REFERENCES assets(id) ON DELETE SET NULL,
    movement_type VARCHAR(30),
    account_amount VARCHAR(512),
    account_currency VARCHAR(10),
    principal_amount TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    FOREIGN KEY (to_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CHECK (LENGTH(currency) BETWEEN 3 AND 10) );

INSERT INTO transactions_catalog ("id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "transaction_date", "description", "notes", "tags", "payee", "is_reconciled", "is_deleted", "created_at", "updated_at", "transfer_id", "payment_method", "liability_id", "external_reference", "payee_id", "currency_id", "original_amount", "original_currency", "conversion_rate", "tranche_id", "real_estate_id", "asset_id", "movement_type", "account_amount", "account_currency", "principal_amount") SELECT "id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "transaction_date", "description", "notes", "tags", "payee", "is_reconciled", "is_deleted", "created_at", "updated_at", "transfer_id", "payment_method", "liability_id", "external_reference", "payee_id", "currency_id", "original_amount", "original_currency", "conversion_rate", "tranche_id", "real_estate_id", "asset_id", "movement_type", "account_amount", "account_currency", "principal_amount" FROM transactions;

DROP TABLE transactions;

ALTER TABLE transactions_catalog RENAME TO transactions;

CREATE INDEX idx_transaction_account_date ON transactions(account_id, transaction_date);

CREATE INDEX idx_transaction_account_deleted ON transactions(account_id, is_deleted);

CREATE INDEX idx_transaction_account_id ON transactions(account_id);

CREATE INDEX idx_transaction_asset_id ON transactions(asset_id);

CREATE INDEX idx_transaction_category_id ON transactions(category_id);

CREATE INDEX idx_transaction_currency_id ON transactions(currency_id);

CREATE INDEX idx_transaction_date ON transactions(transaction_date);

CREATE INDEX idx_transaction_external_reference ON transactions (account_id, external_reference) WHERE external_reference IS NOT NULL;

CREATE INDEX idx_transaction_is_deleted ON transactions(is_deleted);

CREATE INDEX idx_transaction_is_reconciled ON transactions(is_reconciled);

CREATE INDEX idx_transaction_liability_id ON transactions(liability_id);

CREATE INDEX idx_transaction_movement_type ON transactions(movement_type);

CREATE INDEX idx_transaction_payee_id ON transactions(payee_id);

CREATE INDEX idx_transaction_payment_method ON transactions(payment_method);

CREATE INDEX idx_transaction_real_estate_id ON transactions(real_estate_id);

CREATE INDEX idx_transaction_tranche_id ON transactions(tranche_id);

CREATE INDEX idx_transaction_transfer_id ON transactions(transfer_id);

CREATE INDEX idx_transaction_type ON transactions(transaction_type);

CREATE INDEX idx_transaction_user_date ON transactions(user_id, transaction_date);

CREATE INDEX idx_transaction_user_deleted_date ON transactions(user_id, is_deleted, transaction_date DESC);

CREATE INDEX idx_transaction_user_id ON transactions(user_id);

CREATE TABLE transactions_archive_catalog ( id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL,
    account_id INTEGER NOT NULL,
    to_account_id INTEGER,
    transaction_type TEXT NOT NULL CHECK(transaction_type IN ('INCOME','EXPENSE','TRANSFER')),
    amount VARCHAR(512) NOT NULL,
    currency TEXT NOT NULL,
    category_id INTEGER,
    transaction_date TEXT NOT NULL,
    description TEXT,
    notes TEXT,
    tags TEXT,
    payee TEXT,
    is_reconciled INTEGER NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    transfer_id VARCHAR(36),
    payment_method VARCHAR(20),
    liability_id INTEGER,
    external_reference VARCHAR(255),
    created_at TEXT NOT NULL,
    updated_at TEXT,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    payee_id INTEGER,
    currency_id INTEGER,
    original_amount VARCHAR(512),
    original_currency VARCHAR(10),
    conversion_rate NUMERIC(18,
    8),
    tranche_id INTEGER,
    real_estate_id INTEGER,
    asset_id INTEGER,
    movement_type VARCHAR(30),
    account_amount VARCHAR(512),
    account_currency VARCHAR(10),
    principal_amount TEXT);

INSERT INTO transactions_archive_catalog ("id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "transaction_date", "description", "notes", "tags", "payee", "is_reconciled", "is_deleted", "transfer_id", "payment_method", "liability_id", "external_reference", "created_at", "updated_at", "archived_at", "payee_id", "currency_id", "original_amount", "original_currency", "conversion_rate", "tranche_id", "real_estate_id", "asset_id", "movement_type", "account_amount", "account_currency", "principal_amount") SELECT "id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "transaction_date", "description", "notes", "tags", "payee", "is_reconciled", "is_deleted", "transfer_id", "payment_method", "liability_id", "external_reference", "created_at", "updated_at", "archived_at", "payee_id", "currency_id", "original_amount", "original_currency", "conversion_rate", "tranche_id", "real_estate_id", "asset_id", "movement_type", "account_amount", "account_currency", "principal_amount" FROM transactions_archive;

DROP TABLE transactions_archive;

ALTER TABLE transactions_archive_catalog RENAME TO transactions_archive;

CREATE INDEX idx_txn_archive_account ON transactions_archive(account_id);

CREATE INDEX idx_txn_archive_user_date ON transactions_archive(user_id, transaction_date DESC);

CREATE TABLE recurring_transactions_catalog ( id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    account_id INTEGER NOT NULL,
    to_account_id INTEGER,
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('INCOME',
    'EXPENSE',
    'TRANSFER')),
    amount VARCHAR(512) NOT NULL,
    currency TEXT NOT NULL,
    category_id INTEGER,
    description TEXT NOT NULL,
    notes TEXT,
    frequency TEXT NOT NULL CHECK (frequency IN ('DAILY',
    'WEEKLY',
    'BIWEEKLY',
    'MONTHLY',
    'QUARTERLY',
    'YEARLY')),
    next_occurrence TEXT NOT NULL,
    end_date TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    currency_id INTEGER REFERENCES currencies(id),
    anchor_day INTEGER NOT NULL DEFAULT 1 CHECK (anchor_day BETWEEN 1 AND 31),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (to_account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CHECK (LENGTH(currency) BETWEEN 3 AND 10) );

INSERT INTO recurring_transactions_catalog ("id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "description", "notes", "frequency", "next_occurrence", "end_date", "is_active", "created_at", "updated_at", "currency_id", "anchor_day") SELECT "id", "user_id", "account_id", "to_account_id", "transaction_type", "amount", "currency", "category_id", "description", "notes", "frequency", "next_occurrence", "end_date", "is_active", "created_at", "updated_at", "currency_id", "anchor_day" FROM recurring_transactions;

DROP TABLE recurring_transactions;

ALTER TABLE recurring_transactions_catalog RENAME TO recurring_transactions;

CREATE INDEX idx_recurring_account_id ON recurring_transactions(account_id);

CREATE INDEX idx_recurring_active_next_occurrence ON recurring_transactions(is_active, next_occurrence);

CREATE INDEX idx_recurring_frequency ON recurring_transactions(frequency);

CREATE INDEX idx_recurring_is_active ON recurring_transactions(is_active);

CREATE INDEX idx_recurring_next_occurrence ON recurring_transactions(next_occurrence);

CREATE INDEX idx_recurring_transaction_currency_id ON recurring_transactions(currency_id);

CREATE INDEX idx_recurring_user_active ON recurring_transactions(user_id, is_active);

CREATE INDEX idx_recurring_user_active_next ON recurring_transactions(user_id, is_active, next_occurrence);

CREATE INDEX idx_recurring_user_id ON recurring_transactions(user_id);

CREATE TABLE assets_catalog (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id             INTEGER NOT NULL,
    account_id          INTEGER,
    name                VARCHAR(500) NOT NULL,
    asset_type          VARCHAR(20) NOT NULL,
    symbol              VARCHAR(20),
    quantity            DECIMAL(19, 8) NOT NULL,
    purchase_price      DECIMAL(19, 4) NOT NULL,
    current_price       DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    purchase_date       DATE NOT NULL,
    notes               TEXT,
    last_updated        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,

    -- Added in V19
    serial_number       VARCHAR(500),
    brand               VARCHAR(500),
    model               VARCHAR(500),
    condition           VARCHAR(20),
    warranty_expiration DATE,
    useful_life_years   INTEGER,
    photo_path          VARCHAR(500), currency_id INTEGER REFERENCES currencies(id), acquisition_type VARCHAR(20) NOT NULL DEFAULT 'PURCHASE' CHECK (acquisition_type IN ('PURCHASE', 'GIFT', 'PLANNED')), valuation_remainder VARCHAR(512), valuation_recorded_at VARCHAR(40),

    -- Foreign key constraints
    CONSTRAINT fk_assets_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_assets_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(id)
        ON DELETE SET NULL,

    -- Check constraints
    CONSTRAINT chk_asset_quantity_positive
        CHECK (quantity > 0),

    CONSTRAINT chk_asset_purchase_price_non_negative
        CHECK (purchase_price >= 0),

    CONSTRAINT chk_asset_current_price_non_negative
        CHECK (current_price >= 0),

    CONSTRAINT chk_asset_currency_length
        CHECK (LENGTH(currency) BETWEEN 3 AND 10),

    CONSTRAINT chk_asset_type_valid
        CHECK (asset_type IN ('STOCK', 'ETF', 'MUTUAL_FUND', 'BOND', 'CRYPTO', 'COMMODITY', 'REAL_ESTATE', 'VEHICLE', 'JEWELRY', 'COLLECTIBLE', 'ELECTRONICS', 'FURNITURE', 'OTHER'))
);

INSERT INTO assets_catalog ("id", "user_id", "account_id", "name", "asset_type", "symbol", "quantity", "purchase_price", "current_price", "currency", "purchase_date", "notes", "last_updated", "created_at", "updated_at", "serial_number", "brand", "model", "condition", "warranty_expiration", "useful_life_years", "photo_path", "currency_id", "acquisition_type", "valuation_remainder", "valuation_recorded_at") SELECT "id", "user_id", "account_id", "name", "asset_type", "symbol", "quantity", "purchase_price", "current_price", "currency", "purchase_date", "notes", "last_updated", "created_at", "updated_at", "serial_number", "brand", "model", "condition", "warranty_expiration", "useful_life_years", "photo_path", "currency_id", "acquisition_type", "valuation_remainder", "valuation_recorded_at" FROM assets;

DROP TABLE assets;

ALTER TABLE assets_catalog RENAME TO assets;

CREATE INDEX idx_asset_account_id ON assets(account_id);

CREATE INDEX idx_asset_condition ON assets(condition);

CREATE INDEX idx_asset_currency_id ON assets(currency_id);

CREATE INDEX idx_asset_purchase_date ON assets(purchase_date);

CREATE INDEX idx_asset_symbol ON assets(symbol);

CREATE INDEX idx_asset_type ON assets(asset_type);

CREATE INDEX idx_asset_user_account ON assets(user_id, account_id);

CREATE INDEX idx_asset_user_currency
    ON assets(user_id, currency);

CREATE INDEX idx_asset_user_id ON assets(user_id);

CREATE INDEX idx_asset_user_type ON assets(user_id, asset_type);

CREATE INDEX idx_asset_warranty_expiration ON assets(warranty_expiration);

CREATE TABLE liabilities_catalog (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id              INTEGER NOT NULL,
    name                 VARCHAR(512) NOT NULL,
    type                 VARCHAR(20)  NOT NULL,
    principal            VARCHAR(512) NOT NULL,
    current_balance      VARCHAR(512) NOT NULL,
    interest_rate        VARCHAR(512),
    start_date           DATE         NOT NULL,
    end_date             DATE,
    minimum_payment      VARCHAR(512),
    currency VARCHAR(10)   NOT NULL,
    notes                TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    insurance_percentage VARCHAR(512),
    additional_fees      VARCHAR(512),
    institution_id       INTEGER REFERENCES institutions(id), currency_id INTEGER REFERENCES currencies(id), opening_principal TEXT NOT NULL DEFAULT '0', opening_balance TEXT NOT NULL DEFAULT '0', credit_limit TEXT, represented_by_account_id INTEGER REFERENCES accounts(id),

    CONSTRAINT fk_liabilities_new_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_liability_new_currency_length
        CHECK (LENGTH(currency) BETWEEN 3 AND 10),

    CONSTRAINT chk_liability_new_type_valid
        CHECK (type IN (
            'LOAN', 'MORTGAGE', 'CREDIT_CARD', 'PERSONAL_LOAN',
            'STUDENT_LOAN', 'AUTO_LOAN', 'OTHER'
        )),

    CONSTRAINT chk_liability_new_dates_logical
        CHECK (end_date IS NULL OR end_date >= start_date)
);

INSERT INTO liabilities_catalog ("id", "user_id", "name", "type", "principal", "current_balance", "interest_rate", "start_date", "end_date", "minimum_payment", "currency", "notes", "created_at", "updated_at", "insurance_percentage", "additional_fees", "institution_id", "currency_id", "opening_principal", "opening_balance", "credit_limit", "represented_by_account_id") SELECT "id", "user_id", "name", "type", "principal", "current_balance", "interest_rate", "start_date", "end_date", "minimum_payment", "currency", "notes", "created_at", "updated_at", "insurance_percentage", "additional_fees", "institution_id", "currency_id", "opening_principal", "opening_balance", "credit_limit", "represented_by_account_id" FROM liabilities;

DROP TABLE liabilities;

ALTER TABLE liabilities_catalog RENAME TO liabilities;

CREATE INDEX idx_liability_currency_id ON liabilities(currency_id);

CREATE INDEX idx_liability_end_date   ON liabilities(end_date);

CREATE INDEX idx_liability_start_date ON liabilities(start_date);

CREATE INDEX idx_liability_type       ON liabilities(type);

CREATE INDEX idx_liability_user_id    ON liabilities(user_id);

CREATE INDEX idx_liability_user_type  ON liabilities(user_id, type);

CREATE UNIQUE INDEX uq_liability_account_source ON liabilities(represented_by_account_id) WHERE represented_by_account_id IS NOT NULL;

CREATE TABLE liability_tranches_catalog (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id             INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    liability_id        INTEGER NOT NULL REFERENCES liabilities(id) ON DELETE CASCADE,
    tranche_no          INTEGER NOT NULL,
    planned_date        DATE,
    drawn_date          DATE,
    interest_only       INTEGER NOT NULL DEFAULT 0,
    interest_only_until DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    real_estate_id      INTEGER REFERENCES real_estate_properties(id) ON DELETE SET NULL,
    notes               TEXT,
    currency VARCHAR(10) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, planned_amount TEXT NOT NULL DEFAULT '0', drawn_amount TEXT, fee TEXT, direct_disbursement INTEGER NOT NULL DEFAULT 0, reversed_date VARCHAR(10),

    CONSTRAINT uq_liability_tranche_no UNIQUE (liability_id, tranche_no),
    CONSTRAINT chk_tranche_status_valid
        CHECK (status IN ('PLANNED', 'DRAWN', 'CANCELLED')),
    CONSTRAINT chk_tranche_currency_length
        CHECK (LENGTH(currency) BETWEEN 3 AND 10)
);

INSERT INTO liability_tranches_catalog ("id", "user_id", "liability_id", "tranche_no", "planned_date", "drawn_date", "interest_only", "interest_only_until", "status", "real_estate_id", "notes", "currency", "created_at", "updated_at", "planned_amount", "drawn_amount", "fee", "direct_disbursement", "reversed_date") SELECT "id", "user_id", "liability_id", "tranche_no", "planned_date", "drawn_date", "interest_only", "interest_only_until", "status", "real_estate_id", "notes", "currency", "created_at", "updated_at", "planned_amount", "drawn_amount", "fee", "direct_disbursement", "reversed_date" FROM liability_tranches;

DROP TABLE liability_tranches;

ALTER TABLE liability_tranches_catalog RENAME TO liability_tranches;

CREATE INDEX idx_liability_tranche_liability_id ON liability_tranches(liability_id);

CREATE INDEX idx_liability_tranche_status ON liability_tranches(status);

CREATE INDEX idx_liability_tranche_user_id ON liability_tranches(user_id);

CREATE TABLE real_estate_properties_catalog (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id             INTEGER NOT NULL,
    name                VARCHAR(500) NOT NULL,
    address             VARCHAR(1000) NOT NULL,
    property_type       VARCHAR(20) NOT NULL,
    purchase_price      VARCHAR(500) NOT NULL,
    purchase_date       DATE NOT NULL,
    current_value       VARCHAR(500) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    mortgage_id         INTEGER,
    rental_income       VARCHAR(500),
    notes               TEXT,
    documents           TEXT,
    latitude            DECIMAL(10, 7),
    longitude           DECIMAL(10, 7),
    is_active           BOOLEAN NOT NULL DEFAULT 1,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    asset_id            BIGINT, currency_id INTEGER REFERENCES currencies(id), acquisition_type VARCHAR(20) NOT NULL DEFAULT 'PURCHASE' CHECK (acquisition_type IN ('PURCHASE', 'GIFT', 'PLANNED')),
    -- Foreign key constraints
    CONSTRAINT fk_real_estate_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_real_estate_mortgage
        FOREIGN KEY (mortgage_id)
        REFERENCES liabilities(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_real_estate_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets(id)
        ON DELETE SET NULL,
    -- Check constraints
    CONSTRAINT chk_real_estate_currency_length
        CHECK (LENGTH(currency) BETWEEN 3 AND 10),
    CONSTRAINT chk_real_estate_property_type_valid
        CHECK (property_type IN ('RESIDENTIAL', 'COMMERCIAL', 'LAND', 'MIXED_USE', 'INDUSTRIAL', 'OTHER')),
    CONSTRAINT chk_real_estate_latitude_range
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT chk_real_estate_longitude_range
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
);

INSERT INTO real_estate_properties_catalog ("id", "user_id", "name", "address", "property_type", "purchase_price", "purchase_date", "current_value", "currency", "mortgage_id", "rental_income", "notes", "documents", "latitude", "longitude", "is_active", "created_at", "updated_at", "asset_id", "currency_id", "acquisition_type") SELECT "id", "user_id", "name", "address", "property_type", "purchase_price", "purchase_date", "current_value", "currency", "mortgage_id", "rental_income", "notes", "documents", "latitude", "longitude", "is_active", "created_at", "updated_at", "asset_id", "currency_id", "acquisition_type" FROM real_estate_properties;

DROP TABLE real_estate_properties;

ALTER TABLE real_estate_properties_catalog RENAME TO real_estate_properties;

CREATE INDEX idx_real_estate_asset_id ON real_estate_properties(asset_id);

CREATE INDEX idx_real_estate_currency_id ON real_estate_properties(currency_id);

CREATE INDEX idx_real_estate_location ON real_estate_properties(latitude, longitude);

CREATE INDEX idx_real_estate_mortgage_id ON real_estate_properties(mortgage_id);

CREATE INDEX idx_real_estate_property_type ON real_estate_properties(property_type);

CREATE INDEX idx_real_estate_purchase_date ON real_estate_properties(purchase_date);

CREATE INDEX idx_real_estate_user_active ON real_estate_properties(user_id, is_active);

CREATE INDEX idx_real_estate_user_id ON real_estate_properties(user_id);

CREATE INDEX idx_real_estate_user_type ON real_estate_properties(user_id, property_type);

CREATE TABLE account_currency_changes_catalog (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    effective_date TEXT NOT NULL,
    from_currency VARCHAR(10) NOT NULL,
    to_currency VARCHAR(10) NOT NULL,
    rate VARCHAR(512) NOT NULL
);

INSERT INTO account_currency_changes_catalog ("id", "user_id", "account_id", "effective_date", "from_currency", "to_currency", "rate") SELECT "id", "user_id", "account_id", "effective_date", "from_currency", "to_currency", "rate" FROM account_currency_changes;

DROP TABLE account_currency_changes;

ALTER TABLE account_currency_changes_catalog RENAME TO account_currency_changes;

CREATE INDEX idx_account_currency_changes_owner ON account_currency_changes(user_id, account_id, effective_date);

UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE((SELECT seq FROM finance_saved_sequences saved WHERE saved.name = sqlite_sequence.name), seq));
DROP TABLE finance_saved_sequences;
