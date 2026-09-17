ALTER TABLE real_estate_value_history ADD COLUMN is_adjustment BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE account_status_history (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    effective_date VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL
);
CREATE INDEX idx_account_status_owner_date ON account_status_history(user_id, account_id, effective_date, id);
