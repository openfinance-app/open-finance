-- LocalDateConverter binds ISO-8601 text, including null date values.
-- Match the other liability date columns so PostgreSQL accepts loan writes.
ALTER TABLE liabilities
    ALTER COLUMN amortization_start_date TYPE VARCHAR(10)
    USING to_char(amortization_start_date, 'YYYY-MM-DD');
