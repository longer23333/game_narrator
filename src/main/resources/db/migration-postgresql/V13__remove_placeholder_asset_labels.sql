DELETE FROM asset_tag_assignment
WHERE tag_id IN (SELECT id FROM asset_tag WHERE normalized_name IN ('待审核', '待翻译素材'));

DELETE FROM asset_tag_override
WHERE tag_id IN (SELECT id FROM asset_tag WHERE normalized_name IN ('待审核', '待翻译素材'));

DELETE FROM asset_tag
WHERE normalized_name IN ('待审核', '待翻译素材')
  AND id NOT IN (SELECT tag_id FROM asset_tag_assignment)
  AND id NOT IN (SELECT tag_id FROM asset_tag_override);

UPDATE external_asset
SET localized_title = title
WHERE localized_title IN ('待翻译素材', '待审核');
