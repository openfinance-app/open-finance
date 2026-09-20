-- SQLite does not enforce the declared VARCHAR(3) length on net_worth.currency.
-- Existing storage already accepts catalog codes of up to 10 characters; no table rebuild is needed.
SELECT 1;
