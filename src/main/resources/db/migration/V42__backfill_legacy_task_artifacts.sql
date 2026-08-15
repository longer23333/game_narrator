-- Backfill legacy task path columns into the unified artifact index before column retirement.
INSERT INTO artifact (
    id, owner_id, project_id, revision_id, generation_run_id, artifact_type, storage_key,
    mime_type, size_bytes, sha256, schema_version, temporary, expires_at, created_at, deleted_at
)
SELECT
    RANDOM_UUID(), task.owner_id, task.project_id, project.current_revision_id, project.latest_run_id,
    legacy.artifact_type, legacy.storage_key, legacy.mime_type,
    0, REPEAT('0', 64), 0, FALSE, NULL, COALESCE(task.updated_at, task.created_at), NULL
FROM video_tasks task
JOIN video_project project ON project.id = task.project_id
JOIN (
    SELECT id task_id, 'EXTRACTED_AUDIO' artifact_type, extracted_audio_path storage_key, 'audio/wav' mime_type FROM video_tasks WHERE extracted_audio_path IS NOT NULL
    UNION ALL SELECT id, 'SCENE_MANIFEST', scene_manifest_path, 'application/json' FROM video_tasks WHERE scene_manifest_path IS NOT NULL
    UNION ALL SELECT id, 'TRANSCRIPT_TEXT', transcript_text_path, 'text/plain' FROM video_tasks WHERE transcript_text_path IS NOT NULL
    UNION ALL SELECT id, 'TRANSCRIPT_SUBTITLE', subtitle_path, 'application/x-subrip' FROM video_tasks WHERE subtitle_path IS NOT NULL
    UNION ALL SELECT id, 'TRANSCRIPT_DETAIL', transcript_json_path, 'application/json' FROM video_tasks WHERE transcript_json_path IS NOT NULL
    UNION ALL SELECT id, 'VISION_ANALYSIS', visual_analysis_path, 'application/json' FROM video_tasks WHERE visual_analysis_path IS NOT NULL
    UNION ALL SELECT id, 'HIGHLIGHT_MANIFEST', highlight_manifest_path, 'application/json' FROM video_tasks WHERE highlight_manifest_path IS NOT NULL
    UNION ALL SELECT id, 'SCRIPT_MANIFEST', generated_script_path, 'application/json' FROM video_tasks WHERE generated_script_path IS NOT NULL
    UNION ALL SELECT id, 'VOICE_MANIFEST', voice_manifest_path, 'application/json' FROM video_tasks WHERE voice_manifest_path IS NOT NULL
    UNION ALL SELECT id, 'TIMELINE_MANIFEST', timeline_path, 'application/json' FROM video_tasks WHERE timeline_path IS NOT NULL
    UNION ALL SELECT id, 'RENDERED_VIDEO', rendered_video_path, 'video/mp4' FROM video_tasks WHERE rendered_video_path IS NOT NULL
    UNION ALL SELECT id, 'GENERATED_SUBTITLE', generated_subtitle_path, 'application/x-subrip' FROM video_tasks WHERE generated_subtitle_path IS NOT NULL
) legacy ON legacy.task_id = task.id
WHERE task.owner_id IS NOT NULL
  AND task.project_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM artifact existing WHERE existing.storage_key = legacy.storage_key);
