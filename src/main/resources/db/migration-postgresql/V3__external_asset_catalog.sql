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
