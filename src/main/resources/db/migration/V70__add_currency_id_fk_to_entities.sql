-- Add currency_id FK column to entities that store currency as a plain string.
-- Mirrors the pattern already applied in V69 for the transactions table.

-- recurring_transactions
ALTER TABLE recurring_transactions ADD COLUMN currency_id INTEGER REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_recurring_transaction_currency_id ON recurring_transactions(currency_id);

-- budgets
ALTER TABLE budgets ADD COLUMN currency_id INTEGER REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_budget_currency_id ON budgets(currency_id);

-- assets
ALTER TABLE assets ADD COLUMN currency_id INTEGER REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_asset_currency_id ON assets(currency_id);

-- liabilities
ALTER TABLE liabilities ADD COLUMN currency_id INTEGER REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_liability_currency_id ON liabilities(currency_id);

-- real_estate_properties
ALTER TABLE real_estate_properties ADD COLUMN currency_id INTEGER REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_real_estate_currency_id ON real_estate_properties(currency_id);

-- net_worth
ALTER TABLE net_worth ADD COLUMN currency_id INTEGER REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_net_worth_currency_id ON net_worth(currency_id);

-- real_estate_value_history
ALTER TABLE real_estate_value_history ADD COLUMN currency_id INTEGER REFERENCES currencies(id);
CREATE INDEX IF NOT EXISTS idx_re_value_history_currency_id ON real_estate_value_history(currency_id);
