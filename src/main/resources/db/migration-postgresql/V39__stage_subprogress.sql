ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_unit VARCHAR(24);
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_current INTEGER;
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_total INTEGER;
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_detail VARCHAR(240);
