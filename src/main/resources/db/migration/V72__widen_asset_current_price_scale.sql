-- Widen assets.current_price declared scale from DECIMAL(19, 4) to DECIMAL(19, 8)
-- so sub-cent crypto prices are not truncated, aligning it with quantity DECIMAL(19, 8)
-- and the Asset.currentPrice entity mapping.
--
-- In SQLite, DECIMAL/NUMERIC columns use NUMERIC affinity and do NOT enforce the declared
-- precision/scale, so existing current_price values already retain full precision and no data
-- migration is required. SQLite also cannot ALTER a column's type in place, and production runs
-- with hibernate ddl-auto=none (no schema validation). This migration is therefore a documenting
-- no-op kept for versioning consistency (mirroring V38), while the entity mapping drives the
-- DDL on strict databases such as the H2 test schema.

-- ALTER TABLE assets ALTER COLUMN current_price TYPE DECIMAL(19, 8);
;
