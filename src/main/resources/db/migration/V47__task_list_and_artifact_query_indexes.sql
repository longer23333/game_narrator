-- 任务列表查询: WHERE owner_id=? ORDER BY created_at DESC
-- 既有索引为 (owner_id,deleted_at) 与 (owner_id,updated_at),排序仍需额外排序步骤。
CREATE INDEX IF NOT EXISTS idx_video_tasks_owner_created ON video_tasks(owner_id, created_at DESC);

-- 产物定位: JOIN video_tasks ON project_id, WHERE deleted_at IS NULL ORDER BY created_at DESC
-- 批量版 latestAllByTaskIds 与单任务查询共用该访问路径。
CREATE INDEX IF NOT EXISTS idx_artifact_project_created ON artifact(project_id, created_at DESC);
