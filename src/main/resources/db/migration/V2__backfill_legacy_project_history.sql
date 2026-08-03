INSERT INTO project_revision (
    id, project_id, revision_no, parent_revision_id, created_by, change_type,
    change_summary, parameter_snapshot_json, manifest_json,
    manifest_schema_version, manifest_sha256, created_at
)
SELECT
    task.id,
    task.id,
    1,
    NULL,
    '00000000-0000-0000-0000-000000000001',
    'INITIAL',
    '由旧版任务迁移生成的初始工程版本',
    JSON_OBJECT(
        KEY 'name' VALUE task.name,
        KEY 'gameCategory' VALUE task.game_category,
        KEY 'commentaryStyle' VALUE task.commentary_style,
        KEY 'targetDurationSeconds' VALUE task.target_duration_seconds,
        KEY 'taskBrief' VALUE task.task_brief,
        KEY 'sourceVideoPath' VALUE task.source_video_path
    ),
    JSON_OBJECT(
        KEY 'schemaVersion' VALUE 2,
        KEY 'projectId' VALUE CAST(task.id AS VARCHAR),
        KEY 'legacyTaskId' VALUE CAST(task.id AS VARCHAR),
        KEY 'timelinePath' VALUE task.timeline_path,
        KEY 'renderedVideoPath' VALUE task.rendered_video_path
    ),
    2,
    RAWTOHEX(HASH('SHA-256', STRINGTOUTF8('{}'))),
    task.created_at
FROM video_tasks task
WHERE NOT EXISTS (
    SELECT 1 FROM project_revision revision WHERE revision.project_id = task.id
);

UPDATE video_project project
SET current_revision_id = (
    SELECT revision.id
    FROM project_revision revision
    WHERE revision.project_id = project.id AND revision.revision_no = 1
)
WHERE project.current_revision_id IS NULL
  AND EXISTS (SELECT 1 FROM project_revision revision WHERE revision.project_id = project.id);

INSERT INTO interaction_record (
    id, user_id, project_id, generation_run_id, stage_run_id,
    direction, actor_type, interaction_type, content_text, content_json,
    artifact_id, content_sha256, contains_sensitive_data, created_at
)
SELECT
    RANDOM_UUID(),
    '00000000-0000-0000-0000-000000000001',
    task.id,
    NULL,
    NULL,
    'INPUT',
    'USER',
    'FORM',
    task.task_brief,
    JSON_OBJECT(
        KEY 'name' VALUE task.name,
        KEY 'gameCategory' VALUE task.game_category,
        KEY 'commentaryStyle' VALUE task.commentary_style,
        KEY 'targetDurationSeconds' VALUE task.target_duration_seconds,
        KEY 'sourceVideoPath' VALUE task.source_video_path,
        KEY 'migrationSource' VALUE 'video_tasks'
    ),
    NULL,
    RAWTOHEX(HASH('SHA-256', STRINGTOUTF8(COALESCE(task.task_brief, '')))),
    FALSE,
    task.created_at
FROM video_tasks task
WHERE NOT EXISTS (
    SELECT 1
    FROM interaction_record record
    WHERE record.project_id = task.id
      AND record.direction = 'INPUT'
      AND record.interaction_type = 'FORM'
);
