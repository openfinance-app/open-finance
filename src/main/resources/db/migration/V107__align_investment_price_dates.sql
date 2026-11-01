-- SQLite's DATE affinity already accepts ISO-8601 text from LocalDateConverter.
-- PostgreSQL requires a matching type correction; no SQLite rewrite is needed.
SELECT 1;
