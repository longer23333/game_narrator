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
