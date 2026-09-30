CREATE TABLE property_purchase_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    property_id BIGINT NOT NULL,
    mortgage_id BIGINT,
    UNIQUE(user_id, operation_id)
);
