ALTER TABLE clip_compilation ADD COLUMN owner_id UUID;

UPDATE clip_compilation
SET owner_id = '00000000-0000-0000-0000-000000000001'
WHERE owner_id IS NULL;

ALTER TABLE clip_compilation ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE clip_compilation ADD CONSTRAINT fk_clip_compilation_owner
    FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE;

CREATE INDEX idx_clip_compilation_owner_time
    ON clip_compilation(owner_id, created_at DESC);
