CREATE TABLE ownership_operation_receipts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_id VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_id BIGINT NOT NULL,
    UNIQUE (user_id, operation_id)
);
ALTER TABLE assets ADD COLUMN sold_date VARCHAR(10);
ALTER TABLE assets ADD COLUMN sale_transaction_id BIGINT;
ALTER TABLE real_estate_properties ADD COLUMN sold_date VARCHAR(10);
ALTER TABLE real_estate_properties ADD COLUMN sale_transaction_id BIGINT;
ALTER TABLE transactions ADD COLUMN sale_fees VARCHAR(512);
ALTER TABLE transactions_archive ADD COLUMN sale_fees VARCHAR(512);
