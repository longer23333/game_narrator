CREATE TABLE task_file_cleanup_job (
    task_id UUID PRIMARY KEY,
    artifact_paths CLOB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_task_file_cleanup_pending
    ON task_file_cleanup_job(status, created_at);
