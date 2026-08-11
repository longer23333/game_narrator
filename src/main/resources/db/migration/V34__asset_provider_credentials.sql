CREATE TABLE user_asset_provider_config (
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    api_key_ciphertext CLOB NOT NULL,
    api_key_hint VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(user_id,provider),
    CONSTRAINT fk_user_asset_provider_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_asset_provider_name CHECK(provider IN ('PEXELS','PIXABAY'))
);
