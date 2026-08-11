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
