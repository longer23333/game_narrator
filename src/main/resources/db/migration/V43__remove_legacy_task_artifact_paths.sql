-- Retire task-local artifact path columns after V42 copied historical values into artifact.
ALTER TABLE video_tasks DROP COLUMN extracted_audio_path;
ALTER TABLE video_tasks DROP COLUMN scene_manifest_path;
ALTER TABLE video_tasks DROP COLUMN transcript_text_path;
ALTER TABLE video_tasks DROP COLUMN subtitle_path;
ALTER TABLE video_tasks DROP COLUMN transcript_json_path;
ALTER TABLE video_tasks DROP COLUMN visual_analysis_path;
ALTER TABLE video_tasks DROP COLUMN highlight_manifest_path;
ALTER TABLE video_tasks DROP COLUMN generated_script_path;
ALTER TABLE video_tasks DROP COLUMN voice_manifest_path;
ALTER TABLE video_tasks DROP COLUMN timeline_path;
ALTER TABLE video_tasks DROP COLUMN rendered_video_path;
ALTER TABLE video_tasks DROP COLUMN generated_subtitle_path;
