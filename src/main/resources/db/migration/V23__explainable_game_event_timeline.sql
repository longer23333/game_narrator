ALTER TABLE game_events ADD COLUMN IF NOT EXISTS source_frame_index INTEGER;
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS anchor_seconds DOUBLE PRECISION;
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS evidence_json CLOB NOT NULL DEFAULT '[]';
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS confirmation_status VARCHAR(24) NOT NULL DEFAULT 'AI_SUGGESTED';
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS manually_edited BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS knowledge_pack_code VARCHAR(80) NOT NULL DEFAULT 'boss-battle-v1';
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE game_events DROP CONSTRAINT IF EXISTS ck_game_event_confirmation_status;
ALTER TABLE game_events ADD CONSTRAINT ck_game_event_confirmation_status CHECK (
    confirmation_status IN ('AI_SUGGESTED', 'CONFIRMED', 'NEEDS_REVIEW')
);

ALTER TABLE game_events ADD CONSTRAINT IF NOT EXISTS fk_game_event_task
    FOREIGN KEY (task_id) REFERENCES video_tasks(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_game_events_task_time
    ON game_events(task_id, start_seconds, end_seconds);
