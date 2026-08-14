-- Migration V77: Store attachment file contents in the database instead of the filesystem
-- Adds a required file_data BLOB column and drops the filesystem file_path column.
--
-- SQLite does not support ALTER COLUMN or DROP COLUMN (portably), so we recreate the table.
-- There is no existing attachment file data to preserve (the old table only ever stored a
-- filesystem file_path, never file bytes), so the old rows are discarded rather than copied.

-- Step 1: Create the new table
CREATE TABLE IF NOT EXISTS attachments_new (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id            INTEGER NOT NULL,
    entity_type        VARCHAR(25) NOT NULL,
    entity_id          INTEGER NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    file_type          VARCHAR(100) NOT NULL,
    file_size          INTEGER NOT NULL,
    file_data          BLOB NOT NULL,
    uploaded_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description        VARCHAR(500),

    CONSTRAINT fk_attachment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT chk_attachment_entity_type CHECK (entity_type IN (
        'TRANSACTION',
        'ASSET',
        'REAL_ESTATE',
        'LIABILITY',
        'ACCOUNT',
        'RECURRING_TRANSACTION'
    )),

    CONSTRAINT chk_attachment_file_size CHECK (file_size > 0),
    CONSTRAINT chk_attachment_entity_id CHECK (entity_id > 0)
);

-- Step 2: Drop the old table (no file_data to carry over from the filesystem-based schema)
DROP TABLE attachments;

-- Step 3: Rename new table to attachments
ALTER TABLE attachments_new RENAME TO attachments;

-- Step 4: Recreate indexes
CREATE INDEX IF NOT EXISTS idx_attachment_user_id ON attachments(user_id);
CREATE INDEX IF NOT EXISTS idx_attachment_entity ON attachments(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_attachment_uploaded_at ON attachments(uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_attachment_user_entity_type ON attachments(user_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_attachment_user_entity_date ON attachments(user_id, entity_type, entity_id, uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_attachment_file_type ON attachments(file_type);
