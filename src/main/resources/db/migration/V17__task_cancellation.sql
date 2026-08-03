ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS ck_video_tasks_status;
ALTER TABLE video_tasks ADD CONSTRAINT ck_video_tasks_status CHECK (
    status IN ('DRAFT', 'READY', 'PROCESSING', 'WAITING_REVIEW', 'COMPLETED', 'FAILED', 'CANCELLED')
);
