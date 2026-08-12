-- Sanitize imported institution logos that hold values the UI cannot render.
--
-- Skrooge exports put the local icon shape in the t_icon field: either a bare
-- filename ("hellobank.png") or a host-specific absolute filesystem path
-- ("/usr/share/skrooge/images/logo/..."). These were stored verbatim on created
-- institutions, producing broken <img> tags. Null them so the frontend falls back
-- to its placeholder. System logos (bundled /logos/... paths or SVG data URIs),
-- http(s) URLs, and user-uploaded base64 data URIs are left untouched.
UPDATE institutions
SET logo = NULL
WHERE logo IS NOT NULL
  AND logo NOT LIKE 'data:%'
  AND logo NOT LIKE 'http://%'
  AND logo NOT LIKE 'https://%'
  AND logo NOT LIKE '/logos/%';