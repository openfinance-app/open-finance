-- Categories describe the purpose of activity; direction belongs to transactions.
DROP INDEX IF EXISTS idx_category_type;
DROP INDEX IF EXISTS idx_category_user_type;
ALTER TABLE categories DROP COLUMN category_type;
