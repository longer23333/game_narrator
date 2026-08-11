ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_video_tasks_owner_deleted ON video_tasks(owner_id, deleted_at);
