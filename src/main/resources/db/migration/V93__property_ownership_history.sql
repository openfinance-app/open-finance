CREATE TABLE property_status_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    property_id INTEGER NOT NULL REFERENCES real_estate_properties(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    effective_date TEXT NOT NULL,
    is_active BOOLEAN NOT NULL
);
CREATE INDEX idx_property_status_owner_date ON property_status_history(user_id, property_id, effective_date, id);

-- Legacy records have no disposal date. Preserve the last known change date, rather than
-- incorrectly removing the property from its entire ownership history.
INSERT INTO property_status_history(property_id, user_id, effective_date, is_active)
SELECT id, user_id, COALESCE(substr(updated_at, 1, 10), CURRENT_DATE), FALSE
FROM real_estate_properties WHERE is_active = FALSE;
DELETE FROM net_worth WHERE user_id IN (SELECT user_id FROM property_status_history);
