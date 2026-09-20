-- Existing templates retain their currently scheduled day; previously lost anchors cannot be inferred.
ALTER TABLE recurring_transactions ADD COLUMN anchor_day INTEGER NOT NULL DEFAULT 1 CHECK (anchor_day BETWEEN 1 AND 31);
-- LocalDateConverter also accepts legacy epoch seconds/milliseconds stored as text.
-- Use the same local calendar interpretation for those rows as the application converter.
UPDATE recurring_transactions
SET anchor_day = CAST(strftime('%d',
    CASE
        WHEN ltrim(CAST(next_occurrence AS TEXT), '-') NOT GLOB '*[^0-9]*'
             AND length(ltrim(CAST(next_occurrence AS TEXT), '-')) > 0
        THEN datetime(CAST(next_occurrence AS REAL) /
            CASE WHEN length(CAST(next_occurrence AS TEXT)) <= 10 THEN 1 ELSE 1000 END,
            'unixepoch', 'localtime')
        ELSE next_occurrence
    END) AS INTEGER);

ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
CREATE TABLE revoked_tokens (
    token_hash VARCHAR(64) PRIMARY KEY NOT NULL,
    expires_at BIGINT NOT NULL
);
CREATE INDEX idx_revoked_tokens_expiry ON revoked_tokens(expires_at);
