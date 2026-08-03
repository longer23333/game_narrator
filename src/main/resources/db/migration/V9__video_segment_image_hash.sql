ALTER TABLE video_segment_embedding ADD COLUMN image_hash BIGINT;
CREATE INDEX idx_segment_embedding_image_hash ON video_segment_embedding(image_hash);
