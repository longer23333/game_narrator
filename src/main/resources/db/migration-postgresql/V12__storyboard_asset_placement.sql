CREATE TABLE storyboard_asset_placement (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    clip_index INTEGER NOT NULL,
    asset_id UUID NOT NULL,
    placement_type VARCHAR(20) NOT NULL,
    position_name VARCHAR(30) NOT NULL,
    instruction VARCHAR(500),
    ai_assigned BOOLEAN NOT NULL DEFAULT FALSE,
    cutout_applied BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_storyboard_asset_task FOREIGN KEY(task_id) REFERENCES video_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_storyboard_asset_asset FOREIGN KEY(asset_id) REFERENCES external_asset(id) ON DELETE CASCADE,
    CONSTRAINT uk_storyboard_asset_clip UNIQUE(task_id, clip_index, asset_id)
);

CREATE INDEX idx_storyboard_asset_task_clip ON storyboard_asset_placement(task_id, clip_index);
