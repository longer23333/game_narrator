ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_total_bytes BIGINT;
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_etag VARCHAR(500);
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_last_modified VARCHAR(200);
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_error VARCHAR(1000);
ALTER TABLE external_asset ADD COLUMN IF NOT EXISTS download_started_at TIMESTAMP WITH TIME ZONE;
