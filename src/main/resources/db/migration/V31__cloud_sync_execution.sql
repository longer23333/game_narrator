ALTER TABLE cloud_sync_item ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cloud_sync_item ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_cloud_sync_user_type_local ON cloud_sync_item(user_id,item_type,local_id);
CREATE INDEX IF NOT EXISTS idx_cloud_sync_ready ON cloud_sync_item(sync_status,next_attempt_at,updated_at);
