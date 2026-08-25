CREATE TABLE creative_assistant_session (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    brief VARCHAR(2000) NOT NULL,
    plans_json CLOB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    selected_type VARCHAR(20),
    generated_task_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_creative_assistant_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_creative_assistant_task FOREIGN KEY(generated_task_id) REFERENCES video_tasks(id) ON DELETE SET NULL,
    CONSTRAINT ck_creative_assistant_status CHECK(status IN ('PLANNED','APPLIED'))
);
CREATE INDEX idx_creative_assistant_owner_time ON creative_assistant_session(owner_id, created_at DESC);
