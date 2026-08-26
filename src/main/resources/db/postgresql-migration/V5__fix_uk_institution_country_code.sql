-- =============================================================================
-- V5: Correct the non-standard "UK" country code to ISO 3166-1 alpha-2 "GB"
--
-- Mirrors src/main/resources/db/migration/V78 for PostgreSQL deployments.
-- Older seed data stored the United Kingdom as "UK", which is not a valid ISO
-- 3166-1 alpha-2 code; the Financial Map dashboard card resolves centroids by
-- alpha-2 code, so "UK" produced no highlight.
-- =============================================================================
UPDATE institutions
SET country = 'GB'
WHERE country = 'UK';
