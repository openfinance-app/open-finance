ALTER TABLE accounts ADD COLUMN import_source VARCHAR(30);
ALTER TABLE accounts ADD COLUMN liability_converted_at VARCHAR(10);
ALTER TABLE accounts ADD COLUMN liability_transferred_balance VARCHAR(512);
ALTER TABLE liabilities ADD COLUMN converted_from_account_id INTEGER REFERENCES accounts(id);
ALTER TABLE liabilities ADD COLUMN balance_start_date VARCHAR(10);
CREATE UNIQUE INDEX idx_liability_converted_account ON liabilities(converted_from_account_id);

CREATE TABLE liability_conversion_receipts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    account_id INTEGER NOT NULL,
    liability_id INTEGER NOT NULL,
    UNIQUE(user_id, operation_id)
);
