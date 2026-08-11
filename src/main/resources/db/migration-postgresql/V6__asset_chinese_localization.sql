ALTER TABLE external_asset ADD COLUMN localized_title VARCHAR(500);
CREATE INDEX idx_external_asset_localized_title ON external_asset(localized_title);
