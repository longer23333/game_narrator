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
