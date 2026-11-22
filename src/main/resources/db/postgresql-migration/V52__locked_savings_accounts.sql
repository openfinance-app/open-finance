ALTER TABLE accounts DROP CONSTRAINT chk_account_type;
ALTER TABLE accounts ADD CONSTRAINT chk_account_type
    CHECK (account_type IN ('CHECKING', 'SAVINGS', 'LOCKED_SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'CASH', 'OTHER'));
