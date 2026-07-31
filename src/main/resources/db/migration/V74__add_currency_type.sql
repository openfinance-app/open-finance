-- Migration: Add type (FIAT/CRYPTO) column to currencies
-- Version: V74
-- Description: Single source of truth for crypto identity. Backfills the 10 seeded cryptos.

ALTER TABLE currencies
    ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'FIAT'
    CHECK (type IN ('FIAT', 'CRYPTO'));

UPDATE currencies
    SET type = 'CRYPTO'
    WHERE code IN ('BTC', 'ETH', 'BNB', 'XRP', 'ADA', 'SOL', 'DOT', 'DOGE', 'USDT', 'USDC');
