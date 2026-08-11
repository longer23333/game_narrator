CREATE TABLE IF NOT EXISTS source_media_storage (
    task_id UUID PRIMARY KEY,
    storage_mode VARCHAR(20) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    original_filename VARCHAR(500),
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(160),
    filesystem_key VARCHAR(200),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_verified_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_source_media_task FOREIGN KEY (task_id) REFERENCES video_tasks(id) ON DELETE CASCADE,
    CONSTRAINT ck_source_media_mode CHECK (storage_mode IN ('MANAGED','REFERENCED')),
    CONSTRAINT ck_source_media_size CHECK (size_bytes >= 0)
);

CREATE INDEX IF NOT EXISTS idx_source_media_size ON source_media_storage(size_bytes DESC);
