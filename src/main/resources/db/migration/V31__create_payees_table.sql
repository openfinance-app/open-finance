-- Migration: Create payees table
-- Requirement: Payee Management Feature
-- Date: 2026-02-17

-- Create payees table
CREATE TABLE IF NOT EXISTS payees (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(512) NOT NULL,
    logo TEXT,
    category VARCHAR(50),
    is_system BOOLEAN NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_payee_is_system ON payees(is_system);
CREATE INDEX IF NOT EXISTS idx_payee_name ON payees(name);
CREATE INDEX IF NOT EXISTS idx_payee_category ON payees(category);
CREATE INDEX IF NOT EXISTS idx_payee_is_active ON payees(is_active);
