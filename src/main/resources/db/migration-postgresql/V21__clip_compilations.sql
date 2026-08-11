CREATE TABLE clip_compilation (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE clip_compilation_item (
    id UUID PRIMARY KEY,
    compilation_id UUID NOT NULL,
    task_id UUID NOT NULL,
    clip_index INTEGER NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_compilation_item_compilation FOREIGN KEY (compilation_id)
        REFERENCES clip_compilation(id) ON DELETE CASCADE,
    CONSTRAINT fk_compilation_item_task FOREIGN KEY (task_id)
        REFERENCES video_tasks(id) ON DELETE CASCADE,
    CONSTRAINT uk_compilation_item_position UNIQUE (compilation_id, position),
    CONSTRAINT uk_compilation_item_clip UNIQUE (compilation_id, task_id, clip_index)
);

CREATE INDEX idx_compilation_item_order ON clip_compilation_item(compilation_id, position);
