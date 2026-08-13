ALTER TABLE storyboard_asset_placement ADD COLUMN start_offset_seconds DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE storyboard_asset_placement ADD COLUMN end_offset_seconds DOUBLE PRECISION;
ALTER TABLE storyboard_asset_placement ADD COLUMN scale_percent INTEGER NOT NULL DEFAULT 38;
ALTER TABLE storyboard_asset_placement ADD COLUMN animation_name VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE storyboard_asset_placement ADD COLUMN z_index INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_storyboard_asset_layer ON storyboard_asset_placement(task_id,clip_index,z_index,created_at);
