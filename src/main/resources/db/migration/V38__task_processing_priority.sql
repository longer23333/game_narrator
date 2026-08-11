ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS processing_priority INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_video_tasks_processing_priority
    ON video_tasks(processing_priority DESC, created_at ASC);
