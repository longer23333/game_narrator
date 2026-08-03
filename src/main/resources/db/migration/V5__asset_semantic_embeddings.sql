CREATE TABLE asset_embedding (
    asset_id UUID PRIMARY KEY,
    model VARCHAR(120) NOT NULL,
    dimensions INTEGER NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    vector_json CLOB NOT NULL,
    embedded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_embedding_asset FOREIGN KEY(asset_id) REFERENCES external_asset(id)
);

CREATE INDEX idx_asset_embedding_model_hash ON asset_embedding(model, content_hash);
