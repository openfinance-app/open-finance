CREATE TABLE property_status_history (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES real_estate_properties(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    effective_date VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL
);
CREATE INDEX idx_property_status_owner_date ON property_status_history(user_id, property_id, effective_date, id);

-- The legacy last-change date is the only available boundary for inactive properties.
INSERT INTO property_status_history(property_id, user_id, effective_date, is_active)
SELECT id, user_id, COALESCE(substr(CAST(updated_at AS TEXT), 1, 10), CAST(CURRENT_DATE AS TEXT)), FALSE
FROM real_estate_properties WHERE is_active = FALSE;
DELETE FROM net_worth WHERE user_id IN (SELECT user_id FROM property_status_history);
