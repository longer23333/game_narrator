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
