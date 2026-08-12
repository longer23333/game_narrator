-- Generated PostgreSQL baseline through V39. Do not edit; run scripts/build-migration-baselines.ps1.

-- source: V1__database_v2_foundation.sql
CREATE TABLE IF NOT EXISTS video_tasks (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    game_category VARCHAR(40) NOT NULL,
    commentary_style VARCHAR(30) NOT NULL,
    target_duration_seconds INTEGER NOT NULL,
    task_brief VARCHAR(500) NOT NULL,
    source_video_path VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    duration_seconds DOUBLE PRECISION,
    video_width INTEGER,
    video_height INTEGER,
    frames_per_second DOUBLE PRECISION,
    video_codec VARCHAR(80),
    audio_codec VARCHAR(80),
    failure_reason VARCHAR(1000),
    extracted_audio_path VARCHAR(500),
    scene_manifest_path VARCHAR(500),
    detected_scene_count INTEGER,
    transcript_text TEXT,
    transcript_text_path VARCHAR(500),
    subtitle_path VARCHAR(500),
    transcript_json_path VARCHAR(500),
    visual_summary TEXT,
    visual_analysis_path VARCHAR(500),
    analyzed_frame_count INTEGER,
    highlight_summary TEXT,
    highlight_manifest_path VARCHAR(500),
    selected_highlight_count INTEGER,
    generated_title VARCHAR(200),
    script_synopsis TEXT,
    generated_narration TEXT,
    generated_script_path VARCHAR(500),
    generated_script_segment_count INTEGER,
    voice_manifest_path VARCHAR(500),
    generated_voice_segment_count INTEGER,
    timeline_path VARCHAR(500),
    planned_output_duration_seconds DOUBLE PRECISION,
    voice_overflow_count INTEGER,
    rendered_video_path VARCHAR(500),
    generated_subtitle_path VARCHAR(500),
    rendered_file_size_bytes BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS processing_stages (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    stage_type VARCHAR(40) NOT NULL,
    sequence_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INTEGER NOT NULL,
    error_message VARCHAR(1000),
    CONSTRAINT fk_processing_stage_task FOREIGN KEY (task_id) REFERENCES video_tasks(id),
    CONSTRAINT uk_processing_stage_task_type UNIQUE (task_id, stage_type)
);

CREATE TABLE IF NOT EXISTS game_events (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    start_seconds DOUBLE PRECISION NOT NULL,
    end_seconds DOUBLE PRECISION NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    highlight_score DOUBLE PRECISION NOT NULL,
    description VARCHAR(500) NOT NULL
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255),
    role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE video_project (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    game_category VARCHAR(40) NOT NULL,
    commentary_style VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    current_revision_id UUID,
    latest_run_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_video_project_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);

CREATE TABLE media_asset (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID,
    asset_type VARCHAR(30) NOT NULL,
    original_name VARCHAR(255),
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    sha256 CHAR(64) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    duration_ms BIGINT,
    width INTEGER,
    height INTEGER,
    frame_rate DECIMAL(10,4),
    codec VARCHAR(60),
    metadata_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_media_asset_owner FOREIGN KEY (owner_id) REFERENCES app_user(id),
    CONSTRAINT fk_media_asset_project FOREIGN KEY (project_id) REFERENCES video_project(id)
);

CREATE TABLE project_revision (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    revision_no INTEGER NOT NULL,
    parent_revision_id UUID,
    created_by UUID NOT NULL,
    change_type VARCHAR(40) NOT NULL,
    change_summary VARCHAR(500),
    parameter_snapshot_json TEXT NOT NULL,
    manifest_json TEXT NOT NULL,
    manifest_schema_version INTEGER NOT NULL,
    manifest_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_revision_project FOREIGN KEY (project_id) REFERENCES video_project(id),
    CONSTRAINT fk_revision_parent FOREIGN KEY (parent_revision_id) REFERENCES project_revision(id),
    CONSTRAINT fk_revision_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT uk_revision_project_no UNIQUE (project_id, revision_no)
);

CREATE TABLE generation_run (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    input_revision_id UUID NOT NULL,
    output_revision_id UUID,
    run_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    trigger_source VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    elapsed_ms BIGINT,
    failure_code VARCHAR(80),
    failure_message VARCHAR(2000),
    trace_id VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_run_project FOREIGN KEY (project_id) REFERENCES video_project(id),
    CONSTRAINT fk_run_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_run_input_revision FOREIGN KEY (input_revision_id) REFERENCES project_revision(id),
    CONSTRAINT fk_run_output_revision FOREIGN KEY (output_revision_id) REFERENCES project_revision(id)
);

CREATE TABLE stage_run (
    id UUID PRIMARY KEY,
    generation_run_id UUID NOT NULL,
    stage_type VARCHAR(40) NOT NULL,
    attempt_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INTEGER NOT NULL,
    input_snapshot_json TEXT NOT NULL,
    output_summary_json TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    elapsed_ms BIGINT,
    error_code VARCHAR(80),
    error_message VARCHAR(2000),
    CONSTRAINT fk_stage_run_generation FOREIGN KEY (generation_run_id) REFERENCES generation_run(id),
    CONSTRAINT uk_stage_run_attempt UNIQUE (generation_run_id, stage_type, attempt_no),
    CONSTRAINT ck_stage_run_progress CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE artifact (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID NOT NULL,
    revision_id UUID,
    generation_run_id UUID,
    artifact_type VARCHAR(40) NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    schema_version INTEGER,
    temporary BOOLEAN NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_artifact_owner FOREIGN KEY (owner_id) REFERENCES app_user(id),
    CONSTRAINT fk_artifact_project FOREIGN KEY (project_id) REFERENCES video_project(id),
    CONSTRAINT fk_artifact_revision FOREIGN KEY (revision_id) REFERENCES project_revision(id),
    CONSTRAINT fk_artifact_run FOREIGN KEY (generation_run_id) REFERENCES generation_run(id)
);

CREATE TABLE interaction_record (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    project_id UUID,
    generation_run_id UUID,
    stage_run_id UUID,
    direction VARCHAR(10) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    interaction_type VARCHAR(40) NOT NULL,
    content_text TEXT,
    content_json TEXT,
    artifact_id UUID,
    content_sha256 CHAR(64) NOT NULL,
    contains_sensitive_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_interaction_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_interaction_project FOREIGN KEY (project_id) REFERENCES video_project(id),
    CONSTRAINT fk_interaction_run FOREIGN KEY (generation_run_id) REFERENCES generation_run(id),
    CONSTRAINT fk_interaction_stage FOREIGN KEY (stage_run_id) REFERENCES stage_run(id),
    CONSTRAINT fk_interaction_artifact FOREIGN KEY (artifact_id) REFERENCES artifact(id),
    CONSTRAINT ck_interaction_content CHECK (content_text IS NOT NULL OR content_json IS NOT NULL OR artifact_id IS NOT NULL)
);

CREATE TABLE model_invocation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    project_id UUID NOT NULL,
    generation_run_id UUID NOT NULL,
    stage_run_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    model_digest VARCHAR(128),
    operation VARCHAR(40) NOT NULL,
    request_record_id UUID NOT NULL,
    response_record_id UUID,
    parameters_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    elapsed_ms BIGINT,
    queue_ms BIGINT,
    error_code VARCHAR(80),
    error_message VARCHAR(2000),
    CONSTRAINT fk_invocation_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_invocation_project FOREIGN KEY (project_id) REFERENCES video_project(id),
    CONSTRAINT fk_invocation_run FOREIGN KEY (generation_run_id) REFERENCES generation_run(id),
    CONSTRAINT fk_invocation_stage FOREIGN KEY (stage_run_id) REFERENCES stage_run(id),
    CONSTRAINT fk_invocation_request FOREIGN KEY (request_record_id) REFERENCES interaction_record(id),
    CONSTRAINT fk_invocation_response FOREIGN KEY (response_record_id) REFERENCES interaction_record(id)
);

CREATE TABLE token_usage (
    id UUID PRIMARY KEY,
    invocation_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    project_id UUID NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    cached_tokens BIGINT NOT NULL DEFAULT 0,
    image_count INTEGER NOT NULL DEFAULT 0,
    audio_seconds DECIMAL(12,3) NOT NULL DEFAULT 0,
    tts_characters BIGINT NOT NULL DEFAULT 0,
    prompt_eval_ms BIGINT,
    generation_ms BIGINT,
    tokens_per_second DECIMAL(12,3),
    estimated_cost DECIMAL(18,8),
    currency CHAR(3),
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_usage_invocation FOREIGN KEY (invocation_id) REFERENCES model_invocation(id),
    CONSTRAINT fk_usage_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_usage_project FOREIGN KEY (project_id) REFERENCES video_project(id),
    CONSTRAINT ck_usage_tokens CHECK (input_tokens >= 0 AND output_tokens >= 0 AND total_tokens = input_tokens + output_tokens)
);

CREATE TABLE export_preset (
    id UUID PRIMARY KEY,
    owner_id UUID,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    container VARCHAR(20) NOT NULL,
    video_codec VARCHAR(30) NOT NULL,
    audio_codec VARCHAR(30) NOT NULL,
    width INTEGER,
    height INTEGER,
    frame_rate DECIMAL(8,3),
    rate_control VARCHAR(20) NOT NULL,
    quality_value INTEGER,
    target_bitrate_kbps INTEGER,
    max_bitrate_kbps INTEGER,
    hardware_encoder VARCHAR(30),
    audio_bitrate_kbps INTEGER,
    audio_sample_rate INTEGER NOT NULL,
    subtitle_mode VARCHAR(20) NOT NULL,
    color_space VARCHAR(30) NOT NULL,
    extra_options_json TEXT NOT NULL,
    system_preset BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_export_preset_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);

CREATE TABLE export_job (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    revision_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    preset_id UUID,
    export_name VARCHAR(200) NOT NULL,
    settings_snapshot_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INTEGER NOT NULL,
    output_artifact_id UUID,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    downloaded_at TIMESTAMP WITH TIME ZONE,
    download_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(80),
    error_message VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_export_project FOREIGN KEY (project_id) REFERENCES video_project(id),
    CONSTRAINT fk_export_revision FOREIGN KEY (revision_id) REFERENCES project_revision(id),
    CONSTRAINT fk_export_user FOREIGN KEY (requested_by) REFERENCES app_user(id),
    CONSTRAINT fk_export_preset FOREIGN KEY (preset_id) REFERENCES export_preset(id),
    CONSTRAINT fk_export_artifact FOREIGN KEY (output_artifact_id) REFERENCES artifact(id),
    CONSTRAINT ck_export_progress CHECK (progress BETWEEN 0 AND 100)
);

ALTER TABLE video_project ADD CONSTRAINT fk_project_current_revision FOREIGN KEY (current_revision_id) REFERENCES project_revision(id);
ALTER TABLE video_project ADD CONSTRAINT fk_project_latest_run FOREIGN KEY (latest_run_id) REFERENCES generation_run(id);
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS project_id UUID;
ALTER TABLE video_tasks ADD CONSTRAINT fk_legacy_task_owner FOREIGN KEY (owner_id) REFERENCES app_user(id);
ALTER TABLE video_tasks ADD CONSTRAINT fk_legacy_task_project FOREIGN KEY (project_id) REFERENCES video_project(id);

CREATE INDEX idx_project_owner_updated ON video_project(owner_id, updated_at);
CREATE INDEX idx_project_owner_status ON video_project(owner_id, status);
CREATE INDEX idx_run_project_created ON generation_run(project_id, created_at);
CREATE INDEX idx_run_user_created ON generation_run(user_id, created_at);
CREATE INDEX idx_run_status_created ON generation_run(status, created_at);
CREATE INDEX idx_interaction_project_created ON interaction_record(project_id, created_at);
CREATE INDEX idx_usage_user_measured ON token_usage(user_id, measured_at);
CREATE INDEX idx_usage_project_measured ON token_usage(project_id, measured_at);
CREATE INDEX idx_export_project_created ON export_job(project_id, created_at);
CREATE INDEX idx_export_user_created ON export_job(requested_by, created_at);
CREATE INDEX idx_export_status_created ON export_job(status, created_at);

INSERT INTO app_user (id, username, display_name, password_hash, role, status, created_at, updated_at, last_login_at) VALUES ('00000000-0000-0000-0000-000000000001', 'local-user', '本地用户', NULL, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL) ON CONFLICT (id) DO NOTHING;

INSERT INTO video_project (id, owner_id, name, description, game_category, commentary_style, status, created_at, updated_at, version)
SELECT id, '00000000-0000-0000-0000-000000000001', name, task_brief, game_category, commentary_style,
       CASE WHEN status = 'COMPLETED' THEN 'READY' WHEN status = 'FAILED' THEN 'FAILED' WHEN status = 'PROCESSING' THEN 'PROCESSING' ELSE 'DRAFT' END,
       created_at, CURRENT_TIMESTAMP, 0
FROM video_tasks
WHERE NOT EXISTS (SELECT 1 FROM video_project p WHERE p.id = video_tasks.id);

UPDATE video_tasks SET owner_id = '00000000-0000-0000-0000-000000000001' WHERE owner_id IS NULL;
UPDATE video_tasks SET project_id = id WHERE project_id IS NULL;

INSERT INTO export_preset (id, name, description, container, video_codec, audio_codec, width, height, frame_rate, rate_control, quality_value, target_bitrate_kbps, max_bitrate_kbps, hardware_encoder, audio_bitrate_kbps, audio_sample_rate, subtitle_mode, color_space, extra_options_json, system_preset, created_at, updated_at) VALUES
('10000000-0000-0000-0000-000000000001', '快速预览', '720p 快速预览文件', 'MP4', 'H264', 'AAC', 1280, 720, 30, 'CQ', 28, NULL, NULL, 'H264_NVENC', 128, 48000, 'SOFT', 'REC709', '{}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000002', '通用高清', '1080p 通用发布格式', 'MP4', 'H264', 'AAC', 1920, 1080, NULL, 'CQ', 20, NULL, NULL, 'H264_NVENC', 192, 48000, 'SOFT', 'REC709', '{}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000003', '高压缩高清', 'HEVC 高压缩 1080p', 'MP4', 'HEVC', 'AAC', 1920, 1080, NULL, 'CQ', 24, NULL, NULL, 'HEVC_NVENC', 160, 48000, 'SOFT', 'REC709', '{}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000004', '保持源画质', '保持源分辨率和帧率', 'MP4', 'H264', 'AAC', NULL, NULL, NULL, 'CQ', 18, NULL, NULL, 'H264_NVENC', 192, 48000, 'SOFT', 'SOURCE', '{}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000005', '后期编辑', '用于导入专业剪辑软件', 'MOV', 'PRORES', 'PCM', NULL, NULL, NULL, 'CQ', NULL, NULL, NULL, NULL, NULL, 48000, 'SEPARATE_SRT', 'REC709', '{"profile":"422"}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000006', 'Web 发布', '适合网页播放', 'WEBM', 'VP9', 'OPUS', 1920, 1080, 30, 'CQ', 30, NULL, NULL, NULL, 128, 48000, 'NONE', 'REC709', '{}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000007', '纯字幕', '单独导出 SRT 字幕', 'SRT', 'NONE', 'NONE', NULL, NULL, NULL, 'CQ', NULL, NULL, NULL, NULL, NULL, 48000, 'SEPARATE_SRT', 'SOURCE', '{}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('10000000-0000-0000-0000-000000000008', '纯配音', '单独导出 WAV 配音', 'WAV', 'NONE', 'PCM', NULL, NULL, NULL, 'CQ', NULL, NULL, NULL, NULL, NULL, 48000, 'NONE', 'SOURCE', '{}', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- source: V2__backfill_legacy_project_history.sql
-- Clean PostgreSQL installations have no legacy H2 rows to backfill.

-- source: V3__external_asset_catalog.sql
CREATE TABLE external_asset (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    external_id VARCHAR(200) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    creator VARCHAR(300),
    landing_url VARCHAR(2000) NOT NULL,
    preview_url VARCHAR(2000),
    download_url VARCHAR(2000),
    license_code VARCHAR(80) NOT NULL,
    license_url VARCHAR(2000),
    attribution TEXT,
    duration_ms BIGINT,
    local_path VARCHAR(1000),
    import_status VARCHAR(20) NOT NULL DEFAULT 'DISCOVERED',
    metadata_json TEXT NOT NULL,
    discovered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    downloaded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_external_asset_provider_id UNIQUE(provider, external_id)
);

CREATE TABLE asset_tag (
    id UUID PRIMARY KEY,
    normalized_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE asset_tag_assignment (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    tag_source VARCHAR(20) NOT NULL,
    confidence DECIMAL(6,5),
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_tag_assignment_asset FOREIGN KEY(asset_id) REFERENCES external_asset(id),
    CONSTRAINT fk_asset_tag_assignment_tag FOREIGN KEY(tag_id) REFERENCES asset_tag(id),
    CONSTRAINT fk_asset_tag_assignment_user FOREIGN KEY(created_by) REFERENCES app_user(id),
    CONSTRAINT uk_asset_tag_source UNIQUE(asset_id, tag_id, tag_source)
);

CREATE TABLE asset_tag_override (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    action VARCHAR(10) NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_tag_override_asset FOREIGN KEY(asset_id) REFERENCES external_asset(id),
    CONSTRAINT fk_asset_tag_override_tag FOREIGN KEY(tag_id) REFERENCES asset_tag(id),
    CONSTRAINT fk_asset_tag_override_user FOREIGN KEY(user_id) REFERENCES app_user(id),
    CONSTRAINT uk_asset_tag_override UNIQUE(asset_id, tag_id, user_id)
);

CREATE INDEX idx_external_asset_type_title ON external_asset(asset_type, title);
CREATE INDEX idx_external_asset_provider ON external_asset(provider, discovered_at);
CREATE INDEX idx_asset_tag_assignment_asset ON asset_tag_assignment(asset_id, tag_source);

-- source: V4__asset_library_organization.sql
ALTER TABLE external_asset ADD COLUMN favorite BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE external_asset ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_external_asset_library_state ON external_asset(archived, favorite, asset_type, discovered_at);

-- source: V5__asset_semantic_embeddings.sql
CREATE TABLE asset_embedding (
    asset_id UUID PRIMARY KEY,
    model VARCHAR(120) NOT NULL,
    dimensions INTEGER NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    vector_json TEXT NOT NULL,
    embedded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_embedding_asset FOREIGN KEY(asset_id) REFERENCES external_asset(id)
);

CREATE INDEX idx_asset_embedding_model_hash ON asset_embedding(model, content_hash);

-- source: V6__asset_chinese_localization.sql
ALTER TABLE external_asset ADD COLUMN localized_title VARCHAR(500);
CREATE INDEX idx_external_asset_localized_title ON external_asset(localized_title);

-- source: V7__storyboard_review.sql
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS storyboard_review_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS storyboard_approved BOOLEAN NOT NULL DEFAULT TRUE;

-- source: V8__video_segment_semantic_index.sql
CREATE TABLE video_segment_embedding (
    task_id UUID NOT NULL,
    frame_index INTEGER NOT NULL,
    timestamp_seconds DOUBLE PRECISION NOT NULL,
    event_type VARCHAR(60),
    description VARCHAR(1000) NOT NULL,
    image_path VARCHAR(500) NOT NULL,
    model VARCHAR(120) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    vector_json TEXT NOT NULL,
    indexed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(task_id, frame_index),
    CONSTRAINT fk_segment_embedding_task FOREIGN KEY(task_id) REFERENCES video_tasks(id) ON DELETE CASCADE
);
CREATE INDEX idx_segment_embedding_model ON video_segment_embedding(model);

-- source: V9__video_segment_image_hash.sql
ALTER TABLE video_segment_embedding ADD COLUMN image_hash BIGINT;
CREATE INDEX idx_segment_embedding_image_hash ON video_segment_embedding(image_hash);

-- source: V10__allow_storyboard_review_task_status.sql
-- Legacy databases created by Hibernate have an automatically named enum check
-- constraint that predates WAITING_REVIEW. Replace it with a stable Flyway-owned
-- constraint so storyboard review can persist its waiting state.
ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS CONSTRAINT_98;

ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS ck_video_tasks_status;

ALTER TABLE video_tasks ADD CONSTRAINT ck_video_tasks_status CHECK (
    status IN ('DRAFT', 'READY', 'PROCESSING', 'WAITING_REVIEW', 'COMPLETED', 'FAILED')
);

-- source: V11__repair_bilibili_scraped_titles_and_tags.sql
DELETE FROM asset_embedding
WHERE asset_id IN (
    SELECT id FROM external_asset
    WHERE provider='BILIBILI'
      AND (title LIKE '添加至稍后再看%' OR title LIKE '稍后再看%')
);

DELETE FROM asset_tag_assignment
WHERE tag_source IN ('AI','AI_TRANSLATION')
  AND asset_id IN (
    SELECT id FROM external_asset
    WHERE provider='BILIBILI'
      AND (title LIKE '添加至稍后再看%' OR title LIKE '稍后再看%')
);

UPDATE external_asset
SET title=CONCAT('Bilibili 视频 ', REGEXP_REPLACE(landing_url, '^.*/video/(BV[0-9A-Za-z]+).*$', '$1')),
    localized_title=NULL
WHERE provider='BILIBILI'
  AND (title LIKE '添加至稍后再看%' OR title LIKE '稍后再看%');

-- source: V12__storyboard_asset_placement.sql
CREATE TABLE storyboard_asset_placement (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    clip_index INTEGER NOT NULL,
    asset_id UUID NOT NULL,
    placement_type VARCHAR(20) NOT NULL,
    position_name VARCHAR(30) NOT NULL,
    instruction VARCHAR(500),
    ai_assigned BOOLEAN NOT NULL DEFAULT FALSE,
    cutout_applied BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_storyboard_asset_task FOREIGN KEY(task_id) REFERENCES video_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_storyboard_asset_asset FOREIGN KEY(asset_id) REFERENCES external_asset(id) ON DELETE CASCADE,
    CONSTRAINT uk_storyboard_asset_clip UNIQUE(task_id, clip_index, asset_id)
);

CREATE INDEX idx_storyboard_asset_task_clip ON storyboard_asset_placement(task_id, clip_index);

-- source: V13__remove_placeholder_asset_labels.sql
DELETE FROM asset_tag_assignment
WHERE tag_id IN (SELECT id FROM asset_tag WHERE normalized_name IN ('待审核', '待翻译素材'));

DELETE FROM asset_tag_override
WHERE tag_id IN (SELECT id FROM asset_tag WHERE normalized_name IN ('待审核', '待翻译素材'));

DELETE FROM asset_tag
WHERE normalized_name IN ('待审核', '待翻译素材')
  AND id NOT IN (SELECT tag_id FROM asset_tag_assignment)
  AND id NOT IN (SELECT tag_id FROM asset_tag_override);

UPDATE external_asset
SET localized_title = title
WHERE localized_title IN ('待翻译素材', '待审核');

-- source: V14__optional_ai_pipeline.sql
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS cloud_vision_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS ai_script_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS ai_voice_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS auto_assets_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- source: V15__automatic_pipeline_mode.sql
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS automatic_generation_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- source: V16__editing_scope.sql
ALTER TABLE video_tasks ADD COLUMN editing_scope VARCHAR(24) NOT NULL DEFAULT 'FULL_VIDEO';

-- source: V17__task_cancellation.sql
ALTER TABLE video_tasks DROP CONSTRAINT IF EXISTS ck_video_tasks_status;
ALTER TABLE video_tasks ADD CONSTRAINT ck_video_tasks_status CHECK (
    status IN ('DRAFT', 'READY', 'PROCESSING', 'WAITING_REVIEW', 'COMPLETED', 'FAILED', 'CANCELLED')
);

-- source: V18__full_pipeline_failures.sql
ALTER TABLE generation_run ALTER COLUMN failure_message TYPE TEXT;
ALTER TABLE stage_run ALTER COLUMN error_message TYPE TEXT;

-- source: V19__task_glossary.sql
ALTER TABLE video_tasks ADD COLUMN terminology_glossary VARCHAR(4000);

-- source: V20__video_task_optimistic_lock.sql
ALTER TABLE video_tasks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- source: V21__clip_compilations.sql
CREATE TABLE clip_compilation (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE clip_compilation_item (
    id UUID PRIMARY KEY,
    compilation_id UUID NOT NULL,
    task_id UUID NOT NULL,
    clip_index INTEGER NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_compilation_item_compilation FOREIGN KEY (compilation_id)
        REFERENCES clip_compilation(id) ON DELETE CASCADE,
    CONSTRAINT fk_compilation_item_task FOREIGN KEY (task_id)
        REFERENCES video_tasks(id) ON DELETE CASCADE,
    CONSTRAINT uk_compilation_item_position UNIQUE (compilation_id, position),
    CONSTRAINT uk_compilation_item_clip UNIQUE (compilation_id, task_id, clip_index)
);

CREATE INDEX idx_compilation_item_order ON clip_compilation_item(compilation_id, position);

-- source: V22__project_revision_tree.sql
ALTER TABLE project_revision ADD COLUMN revision_label VARCHAR(100);
CREATE INDEX idx_project_revision_parent ON project_revision(project_id, parent_revision_id);

-- source: V23__explainable_game_event_timeline.sql
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS source_frame_index INTEGER;
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS anchor_seconds DOUBLE PRECISION;
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS evidence_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS confirmation_status VARCHAR(24) NOT NULL DEFAULT 'AI_SUGGESTED';
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS manually_edited BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS knowledge_pack_code VARCHAR(80) NOT NULL DEFAULT 'boss-battle-v1';
ALTER TABLE game_events ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE game_events DROP CONSTRAINT IF EXISTS ck_game_event_confirmation_status;
ALTER TABLE game_events ADD CONSTRAINT ck_game_event_confirmation_status CHECK (
    confirmation_status IN ('AI_SUGGESTED', 'CONFIRMED', 'NEEDS_REVIEW')
);

ALTER TABLE game_events ADD CONSTRAINT fk_game_event_task
    FOREIGN KEY (task_id) REFERENCES video_tasks(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_game_events_task_time
    ON game_events(task_id, start_seconds, end_seconds);

-- source: V24__battle_narrative_plan.sql
CREATE TABLE battle_narrative_plan (
    task_id UUID PRIMARY KEY,
    plan_json TEXT NOT NULL,
    confirmed_event_fingerprint CHAR(64) NOT NULL,
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_battle_narrative_plan_task FOREIGN KEY (task_id)
        REFERENCES video_tasks(id) ON DELETE CASCADE
);

-- source: V25__knowledge_packs_and_director_profile.sql
CREATE TABLE IF NOT EXISTS game_knowledge_packs (
    code VARCHAR(80) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    format_version INTEGER NOT NULL,
    pack_json TEXT NOT NULL,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS director_edit_decisions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    clip_index INTEGER NOT NULL,
    decision_type VARCHAR(32) NOT NULL,
    ai_value_json TEXT NOT NULL,
    final_value_json TEXT NOT NULL,
    duration_ratio DOUBLE PRECISION,
    text_density_ratio DOUBLE PRECISION,
    effect_preference VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_director_edit_task FOREIGN KEY (task_id) REFERENCES video_tasks(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_director_edit_created ON director_edit_decisions(created_at);
CREATE INDEX IF NOT EXISTS idx_director_edit_task_clip ON director_edit_decisions(task_id, clip_index);

-- source: V26__source_media_storage_tracking.sql
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

-- source: V27__community_ecosystem.sql
CREATE TABLE community_resource (
    id UUID PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    license_code VARCHAR(40) NOT NULL,
    tags_json TEXT NOT NULL DEFAULT '[]',
    payload_json TEXT NOT NULL,
    format_version INTEGER NOT NULL,
    install_count INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_community_resource UNIQUE(resource_type, code),
    CONSTRAINT ck_community_resource_type CHECK(resource_type IN ('KNOWLEDGE_PACK','EDITING_STYLE')),
    CONSTRAINT ck_community_install_count CHECK(install_count >= 0)
);

CREATE TABLE creative_variant (
    id UUID PRIMARY KEY,
    source_task_id UUID NOT NULL,
    variant_type VARCHAR(20) NOT NULL,
    name VARCHAR(160) NOT NULL,
    strategy_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    generated_task_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_creative_variant_task FOREIGN KEY(source_task_id) REFERENCES video_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_creative_variant_generated_task FOREIGN KEY(generated_task_id) REFERENCES video_tasks(id) ON DELETE SET NULL,
    CONSTRAINT uk_creative_variant_task_type UNIQUE(source_task_id, variant_type),
    CONSTRAINT ck_creative_variant_type CHECK(variant_type IN ('STORY','GUIDE','COMEDY','REVIEW')),
    CONSTRAINT ck_creative_variant_status CHECK(status IN ('PLANNED','APPLIED','RENDERED'))
);

CREATE TABLE editing_decision_report (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    report_json TEXT NOT NULL,
    report_markdown TEXT NOT NULL,
    report_version INTEGER NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_editing_report_task FOREIGN KEY(task_id) REFERENCES video_tasks(id) ON DELETE CASCADE
);

CREATE INDEX idx_community_resource_type_time ON community_resource(resource_type, published_at DESC);
CREATE INDEX idx_creative_variant_task ON creative_variant(source_task_id, created_at DESC);
CREATE INDEX idx_editing_report_task_time ON editing_decision_report(task_id, generated_at DESC);

-- source: V28__local_accounts_and_admin_center.sql
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS account_type VARCHAR(20) NOT NULL DEFAULT 'REGISTERED';
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS storage_quota_bytes BIGINT NOT NULL DEFAULT 107374182400;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS api_monthly_budget DECIMAL(18,6) NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_app_user_email ON app_user(email);

UPDATE app_user SET account_type='ANONYMOUS',display_name='匿名用户' WHERE username='local-user';

CREATE TABLE user_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_sha256 CHAR(64) NOT NULL UNIQUE,
    client_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_user_session_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE user_cloud_ai_config (
    user_id UUID PRIMARY KEY,
    mode VARCHAR(16) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    vision_model VARCHAR(160) NOT NULL,
    text_model VARCHAR(160) NOT NULL,
    api_key_ciphertext TEXT,
    api_key_hint VARCHAR(20),
    input_price_per_million DECIMAL(18,6) NOT NULL DEFAULT 0,
    output_price_per_million DECIMAL(18,6) NOT NULL DEFAULT 0,
    cached_input_price_per_million DECIMAL(18,6) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_ai_config_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE user_ai_usage_daily (
    user_id UUID NOT NULL,
    usage_date DATE NOT NULL,
    provider VARCHAR(40) NOT NULL,
    model_name VARCHAR(160) NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    cached_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(18,8) NOT NULL DEFAULT 0,
    request_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(user_id,usage_date,provider,model_name),
    CONSTRAINT fk_user_ai_usage_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(160),
    detail_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_admin_audit_user FOREIGN KEY(admin_user_id) REFERENCES app_user(id)
);

-- Media bytes remain on disk/object storage; this manifest makes later cloud migration resumable.
CREATE TABLE cloud_sync_item (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    local_id UUID,
    local_path VARCHAR(1000),
    object_key VARCHAR(1000),
    content_sha256 CHAR(64),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    sync_status VARCHAR(24) NOT NULL DEFAULT 'LOCAL_ONLY',
    last_error VARCHAR(1000),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    synced_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_cloud_sync_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS owner_id UUID;
UPDATE external_asset SET owner_id='00000000-0000-0000-0000-000000000001' WHERE owner_id IS NULL;
ALTER TABLE external_asset ADD CONSTRAINT fk_external_asset_owner FOREIGN KEY(owner_id) REFERENCES app_user(id);
CREATE INDEX IF NOT EXISTS idx_external_asset_owner_time ON external_asset(owner_id,discovered_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_session_user_expiry ON user_session(user_id,expires_at);
CREATE INDEX IF NOT EXISTS idx_admin_audit_time ON admin_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cloud_sync_user_status ON cloud_sync_item(user_id,sync_status,updated_at DESC);

-- The offline anonymous account must never inherit administrator privileges.
UPDATE app_user SET role='USER' WHERE username='local-user';

-- source: V29__ai_director_review_board.sql
CREATE TABLE ai_director_review (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_rounds INTEGER NOT NULL,
    completed_rounds INTEGER NOT NULL DEFAULT 0,
    prompt_version VARCHAR(40) NOT NULL,
    model_version VARCHAR(160),
    moderator_summary TEXT,
    consensus_score DECIMAL(5,4),
    final_decision_json TEXT,
    user_action VARCHAR(20),
    user_modification TEXT,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(18,8) NOT NULL DEFAULT 0,
    elapsed_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    applied_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_director_review_task FOREIGN KEY(task_id) REFERENCES video_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_director_review_owner FOREIGN KEY(owner_id) REFERENCES app_user(id),
    CONSTRAINT ck_director_review_rounds CHECK(requested_rounds BETWEEN 1 AND 2),
    CONSTRAINT ck_director_review_status CHECK(status IN ('RUNNING','AWAITING_USER','ACCEPTED','MODIFIED','REJECTED','APPLIED','FAILED'))
);

CREATE TABLE ai_director_agent (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL,
    role_code VARCHAR(40) NOT NULL,
    role_name VARCHAR(80) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    prompt_snapshot TEXT NOT NULL,
    model_version VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_director_agent_review FOREIGN KEY(review_id) REFERENCES ai_director_review(id) ON DELETE CASCADE,
    CONSTRAINT uk_director_agent_role UNIQUE(review_id,role_code)
);

CREATE TABLE ai_director_message (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL,
    agent_id UUID,
    round_no INTEGER NOT NULL,
    sequence_no INTEGER NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    content_json TEXT NOT NULL,
    cited_event_ids TEXT NOT NULL DEFAULT '[]',
    cited_clip_indexes TEXT NOT NULL DEFAULT '[]',
    cited_knowledge_refs TEXT NOT NULL DEFAULT '[]',
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    elapsed_ms BIGINT NOT NULL DEFAULT 0,
    model_version VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_director_message_review FOREIGN KEY(review_id) REFERENCES ai_director_review(id) ON DELETE CASCADE,
    CONSTRAINT fk_director_message_agent FOREIGN KEY(agent_id) REFERENCES ai_director_agent(id) ON DELETE SET NULL,
    CONSTRAINT uk_director_message_sequence UNIQUE(review_id,round_no,sequence_no)
);

CREATE INDEX idx_director_review_task_time ON ai_director_review(task_id,created_at DESC);
CREATE INDEX idx_director_review_owner_time ON ai_director_review(owner_id,created_at DESC);
CREATE INDEX idx_director_message_review_round ON ai_director_message(review_id,round_no,sequence_no);

-- source: V30__per_user_asset_library.sql
CREATE TABLE user_external_asset (
    user_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(user_id,asset_id),
    CONSTRAINT fk_user_external_asset_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_external_asset_asset FOREIGN KEY(asset_id) REFERENCES external_asset(id) ON DELETE CASCADE
);

INSERT INTO user_external_asset(user_id,asset_id,favorite,archived,added_at)
SELECT COALESCE(owner_id,'00000000-0000-0000-0000-000000000001'),id,favorite,archived,discovered_at FROM external_asset;

CREATE INDEX idx_user_external_asset_state ON user_external_asset(user_id,archived,favorite,added_at DESC);

-- source: V31__cloud_sync_execution.sql
ALTER TABLE cloud_sync_item ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cloud_sync_item ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_cloud_sync_user_type_local ON cloud_sync_item(user_id,item_type,local_id);
CREATE INDEX IF NOT EXISTS idx_cloud_sync_ready ON cloud_sync_item(sync_status,next_attempt_at,updated_at);

-- source: V32__task_recycle_bin.sql
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_video_tasks_owner_deleted ON video_tasks(owner_id, deleted_at);

-- source: V33__cloud_sync_retry_policy.sql
ALTER TABLE cloud_sync_item ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_cloud_sync_user_retry ON cloud_sync_item(user_id,sync_status,next_attempt_at);

-- source: V34__asset_provider_credentials.sql
CREATE TABLE user_asset_provider_config (
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    api_key_ciphertext TEXT NOT NULL,
    api_key_hint VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(user_id,provider),
    CONSTRAINT fk_user_asset_provider_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_asset_provider_name CHECK(provider IN ('PEXELS','PIXABAY'))
);

-- source: V35__resumable_asset_downloads.sql
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_total_bytes BIGINT;
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_etag VARCHAR(500);
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_last_modified VARCHAR(200);
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_error VARCHAR(1000);
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_started_at TIMESTAMP WITH TIME ZONE;

-- source: V36__durable_task_file_cleanup.sql
CREATE TABLE task_file_cleanup_job (
    task_id UUID PRIMARY KEY,
    artifact_paths TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_task_file_cleanup_pending
    ON task_file_cleanup_job(status, created_at);

-- source: V37__task_stream_change_tracking.sql
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_video_tasks_owner_updated ON video_tasks(owner_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_processing_stages_task_updated ON processing_stages(task_id, updated_at);

-- source: V38__task_processing_priority.sql
ALTER TABLE video_tasks ADD COLUMN IF NOT EXISTS processing_priority INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_video_tasks_processing_priority
    ON video_tasks(processing_priority DESC, created_at ASC);

-- source: V39__stage_subprogress.sql
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_unit VARCHAR(24);
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_current INTEGER;
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_total INTEGER;
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_detail VARCHAR(240);
