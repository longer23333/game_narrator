DELETE FROM asset_embedding
WHERE asset_id IN (
    SELECT id FROM external_asset
    WHERE provider='BILIBILI'
      AND (title LIKE '添加至稍后再看%' OR title LIKE '稍后再看%')
);

DELETE FROM asset_tag_assignment
WHERE tag_source IN ('AI','AI_TRANSLATION')
  AND asset_id IN (
    SELECT id FROM external_asset
    WHERE provider='BILIBILI'
      AND (title LIKE '添加至稍后再看%' OR title LIKE '稍后再看%')
);

UPDATE external_asset
SET title=CONCAT('Bilibili 视频 ', REGEXP_REPLACE(landing_url, '^.*/video/(BV[0-9A-Za-z]+).*$', '$1')),
    localized_title=NULL
WHERE provider='BILIBILI'
  AND (title LIKE '添加至稍后再看%' OR title LIKE '稍后再看%');
