-- Generated assets reuse external_asset so existing preview, rendering and rights checks apply.
CREATE INDEX IF NOT EXISTS idx_external_asset_generated ON external_asset(provider, asset_type);
