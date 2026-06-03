-- Migration: Drop FTS5 table and create search_tokens for blind-index search
-- Version: V71
-- Description: The transactions_fts FTS5 virtual table stored plaintext of encrypted
--              fields, which is a security hole. Replace it with a search_tokens table
--              that stores deterministic HMAC-based tokens (not reversible).
--              Search is now done by matching tokens rather than full-text search.
-- Author: Open-Finance
-- Date: 2026-05-29

-- Drop the FTS5 virtual table (stores plaintext of encrypted fields)
DROP TABLE IF EXISTS transactions_fts;

-- Create search_tokens table for blind-index keyword search
CREATE TABLE IF NOT EXISTS search_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id INTEGER NOT NULL,
    field_name VARCHAR(30) NOT NULL,
    token VARCHAR(16) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Index for token lookup: user searches for a keyword → look up matching tokens
CREATE INDEX IF NOT EXISTS idx_search_tokens_lookup
    ON search_tokens(user_id, token, entity_type);

-- Index for entity cleanup: when an entity is updated/deleted, remove its old tokens
CREATE INDEX IF NOT EXISTS idx_search_tokens_entity
    ON search_tokens(entity_type, entity_id);
