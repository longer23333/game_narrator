ALTER TABLE project_revision ADD COLUMN revision_label VARCHAR(100);
CREATE INDEX idx_project_revision_parent ON project_revision(project_id, parent_revision_id);
