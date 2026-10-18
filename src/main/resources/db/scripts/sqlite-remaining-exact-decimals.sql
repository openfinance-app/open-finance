-- V102 rebuilds atomically with foreign keys disabled to preserve dependent records.
-- Text affinity prevents SQLite from rounding plaintext decimals through REAL storage.
-- Existing ciphertext and historical values survive; already-lost digits cannot be recovered.
CREATE TEMP TABLE finance_saved_sequences AS SELECT name, seq FROM sqlite_sequence;

CREATE TABLE assets_exact (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    account_id INTEGER,
    name VARCHAR(500) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    symbol VARCHAR(20),
    quantity VARCHAR(512) NOT NULL,
    purchase_price VARCHAR(512) NOT NULL,
    current_price VARCHAR(512) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    purchase_date DATE NOT NULL,
    notes TEXT,
    last_updated TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    serial_number VARCHAR(500),
    brand VARCHAR(500),
    model VARCHAR(500),
    condition VARCHAR(20),
    warranty_expiration DATE,
    useful_life_years INTEGER,
    photo_path VARCHAR(500),
    currency_id INTEGER REFERENCES currencies(id),
    acquisition_type VARCHAR(20) NOT NULL DEFAULT 'PURCHASE' CHECK (acquisition_type IN ('PURCHASE', 'GIFT', 'PLANNED')),
    valuation_remainder VARCHAR(512),
    valuation_recorded_at VARCHAR(40),
    CONSTRAINT fk_assets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_assets_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT chk_asset_current_price_non_negative CHECK (CAST(current_price AS NUMERIC) >= 0),
    CONSTRAINT chk_asset_currency_length CHECK (LENGTH(currency) BETWEEN 3 AND 10),
    CONSTRAINT chk_asset_type_valid CHECK (asset_type IN ('STOCK', 'ETF', 'MUTUAL_FUND', 'BOND', 'CRYPTO', 'COMMODITY', 'REAL_ESTATE', 'VEHICLE', 'JEWELRY', 'COLLECTIBLE', 'ELECTRONICS', 'FURNITURE', 'OTHER'))
);

INSERT INTO assets_exact ("id", "user_id", "account_id", "name", "asset_type", "symbol", "quantity", "purchase_price", "current_price", "currency", "purchase_date", "notes", "last_updated", "created_at", "updated_at", "serial_number", "brand", "model", "condition", "warranty_expiration", "useful_life_years", "photo_path", "currency_id", "acquisition_type", "valuation_remainder", "valuation_recorded_at")
SELECT "id", "user_id", "account_id", "name", "asset_type", "symbol", CAST("quantity" AS TEXT), CAST("purchase_price" AS TEXT), CAST("current_price" AS TEXT), "currency", "purchase_date", "notes", "last_updated", "created_at", "updated_at", "serial_number", "brand", "model", "condition", "warranty_expiration", "useful_life_years", "photo_path", "currency_id", "acquisition_type", "valuation_remainder", "valuation_recorded_at" FROM assets;

DROP TABLE assets;
ALTER TABLE assets_exact RENAME TO assets;

CREATE INDEX idx_asset_account_id ON assets(account_id);

CREATE INDEX idx_asset_condition ON assets(condition);

CREATE INDEX idx_asset_currency_id ON assets(currency_id);

CREATE INDEX idx_asset_purchase_date ON assets(purchase_date);

CREATE INDEX idx_asset_symbol ON assets(symbol);

CREATE INDEX idx_asset_type ON assets(asset_type);

CREATE INDEX idx_asset_user_account ON assets(user_id, account_id);

CREATE INDEX idx_asset_user_currency ON assets(user_id, currency);

CREATE INDEX idx_asset_user_id ON assets(user_id);

CREATE INDEX idx_asset_user_type ON assets(user_id, asset_type);

CREATE INDEX idx_asset_warranty_expiration ON assets(warranty_expiration);

CREATE TABLE transaction_splits_exact (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    category_id    INTEGER REFERENCES categories(id) ON DELETE SET NULL,
    amount VARCHAR(512) NOT NULL,
    description    VARCHAR(2000),  -- encrypted, optional; extra space for AES-256-GCM overhead
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP,
    tags VARCHAR(3000)
);

INSERT INTO transaction_splits_exact ("id", "transaction_id", "category_id", "amount", "description", "created_at", "updated_at", "tags")
SELECT "id", "transaction_id", "category_id", CAST("amount" AS TEXT), "description", "created_at", "updated_at", "tags" FROM transaction_splits;

DROP TABLE transaction_splits;
ALTER TABLE transaction_splits_exact RENAME TO transaction_splits;

CREATE INDEX idx_transaction_split_transaction
    ON transaction_splits(transaction_id);

CREATE INDEX idx_transaction_splits_transaction_id ON transaction_splits(transaction_id);

CREATE TABLE net_worth_exact (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    snapshot_date DATE NOT NULL,
    total_assets VARCHAR(512) NOT NULL,
    total_liabilities VARCHAR(512) NOT NULL DEFAULT 0.00,
    net_worth VARCHAR(512) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, currency_id INTEGER REFERENCES currencies(id),

    -- Foreign key constraint
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO net_worth_exact ("id", "user_id", "snapshot_date", "total_assets", "total_liabilities", "net_worth", "currency", "created_at", "currency_id")
SELECT "id", "user_id", "snapshot_date", CAST("total_assets" AS TEXT), CAST("total_liabilities" AS TEXT), CAST("net_worth" AS TEXT), "currency", "created_at", "currency_id" FROM net_worth;

DROP TABLE net_worth;
ALTER TABLE net_worth_exact RENAME TO net_worth;

CREATE INDEX idx_net_worth_currency_id ON net_worth(currency_id);

CREATE INDEX idx_net_worth_snapshot_date ON net_worth(snapshot_date);

CREATE UNIQUE INDEX idx_net_worth_user_date ON net_worth(user_id, snapshot_date);

CREATE INDEX idx_net_worth_user_date_desc
    ON net_worth(user_id, snapshot_date DESC);

CREATE TABLE interest_rate_variations_exact (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    rate VARCHAR(512) NOT NULL,
    tax_rate VARCHAR(512) DEFAULT 0.0000,
    valid_from DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,

    FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
);

INSERT INTO interest_rate_variations_exact ("id", "account_id", "rate", "tax_rate", "valid_from", "created_at", "updated_at")
SELECT "id", "account_id", CAST("rate" AS TEXT), CAST("tax_rate" AS TEXT), "valid_from", "created_at", "updated_at" FROM interest_rate_variations;

DROP TABLE interest_rate_variations;
ALTER TABLE interest_rate_variations_exact RENAME TO interest_rate_variations;

CREATE INDEX idx_interest_variation_account ON interest_rate_variations(account_id);

UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE((SELECT seq FROM finance_saved_sequences saved WHERE saved.name = sqlite_sequence.name), seq));
INSERT INTO sqlite_sequence (name, seq)
SELECT name, seq FROM finance_saved_sequences saved
WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence current WHERE current.name = saved.name);
DROP TABLE finance_saved_sequences;
