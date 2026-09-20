-- Retire a fixture accidentally shipped in the system payee catalogue.
-- User-owned payees and all current/archived transaction references are preserved.
DELETE FROM payees
WHERE is_system = TRUE AND user_id IS NULL AND name = 'Loan Payment Test'
  AND NOT EXISTS (SELECT 1 FROM transactions WHERE transactions.payee_id = payees.id)
  AND NOT EXISTS (SELECT 1 FROM transactions_archive WHERE transactions_archive.payee_id = payees.id);

UPDATE payees SET is_active = FALSE
WHERE is_system = TRUE AND user_id IS NULL AND name = 'Loan Payment Test';
