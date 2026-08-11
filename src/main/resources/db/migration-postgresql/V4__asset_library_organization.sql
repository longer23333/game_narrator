ALTER TABLE external_asset ADD COLUMN favorite BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE external_asset ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_external_asset_library_state ON external_asset(archived, favorite, asset_type, discovered_at);
