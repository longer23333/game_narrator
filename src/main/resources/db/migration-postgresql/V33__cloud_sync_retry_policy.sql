ALTER TABLE cloud_sync_item ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_cloud_sync_user_retry ON cloud_sync_item(user_id,sync_status,next_attempt_at);
