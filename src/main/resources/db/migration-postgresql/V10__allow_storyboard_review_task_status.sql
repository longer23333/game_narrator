-- Legacy databases created by Hibernate have an automatically named enum check
-- constraint that predates WAITING_REVIEW. Replace it with a stable Flyway-owned
-- constraint so storyboard review can persist its waiting state.
ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS CONSTRAINT_98;

ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS ck_video_tasks_status;

ALTER TABLE video_tasks ADD CONSTRAINT ck_video_tasks_status CHECK (
    status IN ('DRAFT', 'READY', 'PROCESSING', 'WAITING_REVIEW', 'COMPLETED', 'FAILED')
);
