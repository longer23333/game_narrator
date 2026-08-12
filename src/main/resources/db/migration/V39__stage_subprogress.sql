ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_unit VARCHAR(24);
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_current INT;
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_total INT;
ALTER TABLE processing_stages ADD COLUMN IF NOT EXISTS subprogress_detail VARCHAR(240);
