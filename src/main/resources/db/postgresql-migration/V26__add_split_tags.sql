-- Tags on a split retain their original scope. Existing parent tags cannot be
-- assigned to individual parts without the source data, so leave them untouched.
ALTER TABLE transaction_splits ADD COLUMN tags VARCHAR(3000);
