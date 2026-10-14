-- Category icons are emoji, not icon-library names. Preserve all customized icons.
UPDATE categories SET icon = '📁' WHERE icon = 'tag' AND is_system = FALSE;
