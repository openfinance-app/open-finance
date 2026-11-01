-- LocalDateConverter persists ISO-8601 text, including null date bindings.
-- Keep current and archived transaction storage consistent with that converter.
ALTER TABLE transactions
    ALTER COLUMN investment_price_date TYPE VARCHAR(10)
    USING to_char(investment_price_date, 'YYYY-MM-DD');
ALTER TABLE transactions_archive
    ALTER COLUMN investment_price_date TYPE VARCHAR(10)
    USING to_char(investment_price_date, 'YYYY-MM-DD');
