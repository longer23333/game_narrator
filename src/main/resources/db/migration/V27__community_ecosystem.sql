CREATE TABLE community_resource (
    id UUID PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    license_code VARCHAR(40) NOT NULL,
    tags_json CLOB NOT NULL DEFAULT '[]',
    payload_json CLOB NOT NULL,
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
    strategy_json CLOB NOT NULL,
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
    report_json CLOB NOT NULL,
    report_markdown CLOB NOT NULL,
    report_version INTEGER NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_editing_report_task FOREIGN KEY(task_id) REFERENCES video_tasks(id) ON DELETE CASCADE
);

CREATE INDEX idx_community_resource_type_time ON community_resource(resource_type, published_at DESC);
CREATE INDEX idx_creative_variant_task ON creative_variant(source_task_id, created_at DESC);
CREATE INDEX idx_editing_report_task_time ON editing_decision_report(task_id, generated_at DESC);
