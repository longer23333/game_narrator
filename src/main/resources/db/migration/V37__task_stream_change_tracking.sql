ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_video_tasks_owner_updated ON video_tasks(owner_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_processing_stages_task_updated ON processing_stages(task_id, updated_at);
