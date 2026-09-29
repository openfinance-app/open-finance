ALTER TABLE ai_conversations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE insights ADD COLUMN source_key VARCHAR(200);
CREATE UNIQUE INDEX uk_insight_user_source ON insights (user_id, source_key);
