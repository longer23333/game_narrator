ALTER TABLE storyboard_asset_placement ADD COLUMN volume_percent INTEGER NOT NULL DEFAULT 48;
ALTER TABLE storyboard_asset_placement ADD COLUMN fade_in_seconds DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE storyboard_asset_placement ADD COLUMN fade_out_seconds DOUBLE PRECISION NOT NULL DEFAULT 0;
