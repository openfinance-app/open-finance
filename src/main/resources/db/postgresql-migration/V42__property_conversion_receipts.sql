CREATE TABLE property_conversion_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    UNIQUE(user_id, operation_id)
);
