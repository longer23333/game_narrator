CREATE TABLE video_segment_embedding (
    task_id UUID NOT NULL,
    frame_index INTEGER NOT NULL,
    timestamp_seconds DOUBLE PRECISION NOT NULL,
    event_type VARCHAR(60),
    description VARCHAR(1000) NOT NULL,
    image_path VARCHAR(500) NOT NULL,
    model VARCHAR(120) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    vector_json TEXT NOT NULL,
    indexed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(task_id, frame_index),
    CONSTRAINT fk_segment_embedding_task FOREIGN KEY(task_id) REFERENCES video_tasks(id) ON DELETE CASCADE
);
CREATE INDEX idx_segment_embedding_model ON video_segment_embedding(model);
