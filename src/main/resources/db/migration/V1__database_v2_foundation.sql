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
    transcript_text CLOB,
    transcript_text_path VARCHAR(500),
    subtitle_path VARCHAR(500),
    transcript_json_path VARCHAR(500),
    visual_summary CLOB,
    visual_analysis_path VARCHAR(500),
    analyzed_frame_count INTEGER,
    highlight_summary CLOB,
    highlight_manifest_path VARCHAR(500),
    selected_highlight_count INTEGER,
    generated_title VARCHAR(200),
    script_synopsis CLOB,
    generated_narration CLOB,
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
    metadata_json CLOB NOT NULL,
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
    parameter_snapshot_json CLOB NOT NULL,
    manifest_json CLOB NOT NULL,
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
    input_snapshot_json CLOB NOT NULL,
    output_summary_json CLOB,
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
    content_text CLOB,
    content_json CLOB,
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
    parameters_json CLOB NOT NULL,
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
    extra_options_json CLOB NOT NULL,
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
    settings_snapshot_json CLOB NOT NULL,
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
ALTER TABLE video_tasks ADD CONSTRAINT IF NOT EXISTS fk_legacy_task_owner FOREIGN KEY (owner_id) REFERENCES app_user(id);
ALTER TABLE video_tasks ADD CONSTRAINT IF NOT EXISTS fk_legacy_task_project FOREIGN KEY (project_id) REFERENCES video_project(id);

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

MERGE INTO app_user (id, username, display_name, password_hash, role, status, created_at, updated_at, last_login_at)
KEY (id) VALUES ('00000000-0000-0000-0000-000000000001', 'local-user', '本地用户', NULL, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL);

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
