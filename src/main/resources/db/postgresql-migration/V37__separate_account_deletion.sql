-- Closed accounts retain their financial position. Deletion has its own dated marker.
ALTER TABLE accounts ADD COLUMN deleted_date VARCHAR(10);

-- Recover only explicit deletions from the audit trail. Skrooge's closed flag is not deletion.
UPDATE accounts
SET deleted_date = (
    SELECT DATE(COALESCE(h.redone_at, h.created_at)) FROM operation_history h
    WHERE h.entity_type = 'ACCOUNT' AND h.entity_id = accounts.id AND h.user_id = accounts.user_id
      AND (h.undone_at IS NULL OR h.redone_at IS NOT NULL)
    ORDER BY COALESCE(h.redone_at, h.created_at) DESC, h.id DESC LIMIT 1
)
WHERE is_active = FALSE AND (
    SELECT h.operation_type FROM operation_history h
    WHERE h.entity_type = 'ACCOUNT' AND h.entity_id = accounts.id AND h.user_id = accounts.user_id
      AND (h.undone_at IS NULL OR h.redone_at IS NOT NULL)
    ORDER BY COALESCE(h.redone_at, h.created_at) DESC, h.id DESC LIMIT 1
) = 'DELETE';

CREATE INDEX idx_account_owner_deleted ON accounts(user_id, deleted_date);
