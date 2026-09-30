CREATE TABLE property_purchase_receipts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    property_id INTEGER NOT NULL,
    mortgage_id INTEGER,
    UNIQUE(user_id, operation_id)
);
