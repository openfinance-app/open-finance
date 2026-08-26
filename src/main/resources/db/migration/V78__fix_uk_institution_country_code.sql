-- Correct the non-standard "UK" country code to the ISO 3166-1 alpha-2 code "GB".
--
-- Older seed data (and any user-entered value) stored the United Kingdom as "UK",
-- which is not a valid ISO 3166-1 alpha-2 code. The Financial Map dashboard card
-- resolves country centroids by alpha-2 code, so "UK" produced no highlight.
UPDATE institutions
SET country = 'GB'
WHERE country = 'UK';
