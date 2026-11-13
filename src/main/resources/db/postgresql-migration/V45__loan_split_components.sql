ALTER TABLE transaction_splits ADD COLUMN loan_component VARCHAR(16)
    CHECK (loan_component IN ('PRINCIPAL', 'INTEREST', 'INSURANCE', 'FEE', 'OTHER'));
