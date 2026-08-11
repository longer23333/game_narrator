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
