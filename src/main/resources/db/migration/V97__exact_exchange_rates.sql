-- Keep future rate precision as decimal text. Existing rounded values are retained as-is.
CREATE TABLE exchange_rates_exact (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    base_currency VARCHAR(10) NOT NULL,
    target_currency VARCHAR(10) NOT NULL,
    rate VARCHAR(512) NOT NULL,
    rate_date DATE NOT NULL,
    source VARCHAR(100) DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exchange_rate_currencies_date UNIQUE (base_currency, target_currency, rate_date),
    CONSTRAINT chk_exchange_rate_positive CHECK (CAST(rate AS REAL) > 0),
    CONSTRAINT chk_base_currency_format CHECK (LENGTH(base_currency) BETWEEN 3 AND 10
        AND base_currency = UPPER(base_currency) AND base_currency NOT GLOB '*[^A-Z]*'),
    CONSTRAINT chk_target_currency_format CHECK (LENGTH(target_currency) BETWEEN 3 AND 10
        AND target_currency = UPPER(target_currency) AND target_currency NOT GLOB '*[^A-Z]*')
);
INSERT INTO exchange_rates_exact (id, base_currency, target_currency, rate, rate_date, source, created_at)
SELECT id, base_currency, target_currency, CAST(rate AS TEXT), rate_date, source, created_at FROM exchange_rates;
DROP TABLE exchange_rates;
ALTER TABLE exchange_rates_exact RENAME TO exchange_rates;
CREATE INDEX idx_exchange_rate_base ON exchange_rates(base_currency);
CREATE INDEX idx_exchange_rate_target ON exchange_rates(target_currency);
CREATE INDEX idx_exchange_rate_date ON exchange_rates(rate_date);
CREATE INDEX idx_exchange_rate_base_target_date ON exchange_rates(base_currency, target_currency, rate_date);
CREATE INDEX idx_exchange_rate_currencies_date ON exchange_rates(base_currency, target_currency, rate_date DESC);

ALTER TABLE transactions ADD COLUMN exact_conversion_rate VARCHAR(512);
UPDATE transactions SET exact_conversion_rate = CAST(conversion_rate AS TEXT);
ALTER TABLE transactions DROP COLUMN conversion_rate;
ALTER TABLE transactions RENAME COLUMN exact_conversion_rate TO conversion_rate;
ALTER TABLE transactions_archive ADD COLUMN exact_conversion_rate VARCHAR(512);
UPDATE transactions_archive SET exact_conversion_rate = CAST(conversion_rate AS TEXT);
ALTER TABLE transactions_archive DROP COLUMN conversion_rate;
ALTER TABLE transactions_archive RENAME COLUMN exact_conversion_rate TO conversion_rate;
