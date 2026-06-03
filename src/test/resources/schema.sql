-- H2-compatible schema for tables not managed by JPA entities.
-- Runs after Hibernate ddl-auto creates entity-based tables.

CREATE TABLE IF NOT EXISTS search_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    field_name VARCHAR(30) NOT NULL,
    token VARCHAR(16) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_search_tokens_lookup
    ON search_tokens(user_id, token, entity_type);

CREATE INDEX IF NOT EXISTS idx_search_tokens_entity
    ON search_tokens(entity_type, entity_id);
