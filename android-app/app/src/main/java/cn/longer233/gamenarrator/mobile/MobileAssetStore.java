package cn.longer233.gamenarrator.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for the asset catalog and project-scoped asset placements.
 */
public final class MobileAssetStore {
    public static final class AssetInfo {
        private final long id, createdAt;
        private final String uri, name, type, tags;
        AssetInfo(long id, String uri, String name, String type, String tags, long createdAt) {
            this.id = id;
            this.uri = uri;
            this.name = name;
            this.type = type;
            this.tags = tags;
            this.createdAt = createdAt;
        }
        public long id() { return id; }
        public String uri() { return uri; }
        public String name() { return name; }
        public String type() { return type; }
        public String tags() { return tags; }
        public long createdAt() { return createdAt; }
    }

    public static final class PlacementInfo {
        private final long id, assetId, fadeInMs, fadeOutMs;
        private final String clipKey, name, type, uri, role;
        private final float volume, visualX, visualY, visualScale;
        PlacementInfo(long id, long assetId, String clipKey, String name, String type, String uri, String role,
                      float volume, long fadeInMs, long fadeOutMs, float visualX, float visualY, float visualScale) {
            this.id = id;
            this.assetId = assetId;
            this.clipKey = clipKey;
            this.name = name;
            this.type = type;
            this.uri = uri;
            this.role = role;
            this.volume = volume;
            this.fadeInMs = fadeInMs;
            this.fadeOutMs = fadeOutMs;
            this.visualX = visualX;
            this.visualY = visualY;
            this.visualScale = visualScale;
        }
        public long id() { return id; }
        public long assetId() { return assetId; }
        public String clipKey() { return clipKey; }
        public String name() { return name; }
        public String type() { return type; }
        public String uri() { return uri; }
        public float volume() { return volume; }
        public long fadeInMs() { return fadeInMs; }
        public long fadeOutMs() { return fadeOutMs; }
        public float visualX() { return visualX; }
        public float visualY() { return visualY; }
        public float visualScale() { return visualScale; }
        public String role() { return role; }
    }

    private final MobileDatabase database;

    public MobileAssetStore(MobileDatabase database) {
        this.database = database;
    }

    public long addAsset(String uri, String name, String type) {
        ContentValues row = new ContentValues();
        row.put("uri", uri);
        row.put("name", name);
        row.put("media_type", type);
        row.put("tags", "");
        row.put("created_at", System.currentTimeMillis());
        long id = database.getWritableDatabase().insertWithOnConflict("asset", null, row, SQLiteDatabase.CONFLICT_IGNORE);
        if (id >= 0) return id;
        try (Cursor c = database.getReadableDatabase().query("asset", new String[]{"id"}, "uri=?", new String[]{uri}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    public List<AssetInfo> listAssets() {
        List<AssetInfo> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query("asset",
                new String[]{"id", "uri", "name", "media_type", "tags", "created_at"}, null, null, null, null, "created_at DESC")) {
            while (c.moveToNext()) {
                out.add(new AssetInfo(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5)));
            }
        }
        return out;
    }

    public void updateAssetTags(long id, String tags) {
        ContentValues row = new ContentValues();
        row.put("tags", tags == null ? "" : tags.trim());
        database.getWritableDatabase().update("asset", row, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteAsset(long id) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("asset_placement", "asset_id=?", new String[]{String.valueOf(id)});
            db.delete("asset", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void placeAsset(long projectId, String clipKey, long assetId, String role) {
        ContentValues row = new ContentValues();
        row.put("project_id", projectId);
        row.put("clip_key", clipKey);
        row.put("asset_id", assetId);
        row.put("role", role);
        row.put("created_at", System.currentTimeMillis());
        database.getWritableDatabase().insertWithOnConflict("asset_placement", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<PlacementInfo> listPlacements(long projectId) {
        List<PlacementInfo> out = new ArrayList<>();
        String sql = "SELECT p.id,p.asset_id,p.clip_key,a.name,a.media_type,a.uri,p.role,p.volume,p.fade_in_ms,p.fade_out_ms,p.visual_x,p.visual_y,p.visual_scale FROM asset_placement p JOIN asset a ON a.id=p.asset_id WHERE p.project_id=? ORDER BY p.created_at";
        try (Cursor c = database.getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(projectId)})) {
            while (c.moveToNext()) {
                out.add(new PlacementInfo(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                        c.getString(5), c.getString(6), c.getFloat(7), c.getLong(8), c.getLong(9), c.getFloat(10),
                        c.getFloat(11), c.getFloat(12)));
            }
        }
        return out;
    }

    public void updatePlacementMix(long projectId, long id, float volume, long fadeInMs, long fadeOutMs) {
        ContentValues row = new ContentValues();
        row.put("volume", Math.max(0f, Math.min(2f, volume)));
        row.put("fade_in_ms", Math.max(0, fadeInMs));
        row.put("fade_out_ms", Math.max(0, fadeOutMs));
        database.getWritableDatabase().update("asset_placement", row, "id=? AND project_id=?",
                new String[]{String.valueOf(id), String.valueOf(projectId)});
    }

    public void updatePlacementVisual(long projectId, long id, float x, float y, float scale) {
        ContentValues row = new ContentValues();
        row.put("visual_x", Math.max(-1f, Math.min(1f, x)));
        row.put("visual_y", Math.max(-1f, Math.min(1f, y)));
        row.put("visual_scale", Math.max(.1f, Math.min(1f, scale)));
        database.getWritableDatabase().update("asset_placement", row, "id=? AND project_id=?",
                new String[]{String.valueOf(id), String.valueOf(projectId)});
    }

    public void removePlacement(long projectId, long id) {
        database.getWritableDatabase().delete("asset_placement", "id=? AND project_id=?",
                new String[]{String.valueOf(id), String.valueOf(projectId)});
    }

    public void removePlacementsForClip(long projectId, String clipKey) {
        database.getWritableDatabase().delete("asset_placement", "project_id=? AND clip_key=?",
                new String[]{String.valueOf(projectId), clipKey});
    }

    public void removePlacementsForRole(long projectId, String clipKey, String role) {
        database.getWritableDatabase().delete("asset_placement", "project_id=? AND clip_key=? AND role=?",
                new String[]{String.valueOf(projectId), clipKey, role});
    }

    public void replacePlacements(long projectId, List<ProjectSnapshot.Placement> placements) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("asset_placement", "project_id=?", new String[]{String.valueOf(projectId)});
            if (placements != null) {
                for (ProjectSnapshot.Placement placement : placements) {
                    ContentValues row = new ContentValues();
                    row.put("project_id", projectId);
                    row.put("clip_key", placement.clipKey());
                    row.put("asset_id", placement.assetId());
                    row.put("role", placement.role());
                    row.put("volume", Math.max(0f, Math.min(2f, placement.volume())));
                    row.put("fade_in_ms", Math.max(0, placement.fadeInMs()));
                    row.put("fade_out_ms", Math.max(0, placement.fadeOutMs()));
                    row.put("visual_x", Math.max(-1f, Math.min(1f, placement.visualX())));
                    row.put("visual_y", Math.max(-1f, Math.min(1f, placement.visualY())));
                    row.put("visual_scale", Math.max(.1f, Math.min(1f, placement.visualScale())));
                    row.put("created_at", System.currentTimeMillis());
                    db.insertOrThrow("asset_placement", null, row);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void copyPlacements(long projectId, String fromClipKey, String toClipKey) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO asset_placement(project_id,clip_key,asset_id,role,volume,fade_in_ms,fade_out_ms,visual_x,visual_y,visual_scale,created_at) SELECT project_id,?,asset_id,role,volume,fade_in_ms,fade_out_ms,visual_x,visual_y,visual_scale,created_at FROM asset_placement WHERE project_id=? AND clip_key=?",
                    new Object[]{toClipKey, projectId, fromClipKey});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void deletePlacementsForProject(SQLiteDatabase db, long projectId) {
        db.delete("asset_placement", "project_id=?", new String[]{String.valueOf(projectId)});
    }

    void duplicatePlacementsForProject(SQLiteDatabase db, long targetProjectId, long sourceProjectId) {
        db.execSQL("INSERT INTO asset_placement(project_id,clip_key,asset_id,role,volume,fade_in_ms,fade_out_ms,visual_x,visual_y,visual_scale,created_at) SELECT ?,clip_key,asset_id,role,volume,fade_in_ms,fade_out_ms,visual_x,visual_y,visual_scale,? FROM asset_placement WHERE project_id=?",
                new Object[]{targetProjectId, System.currentTimeMillis(), sourceProjectId});
    }

    void forkPlacementsForProject(SQLiteDatabase db, long targetProjectId, long sourceProjectId, long revisionId) {
        db.execSQL("INSERT OR IGNORE INTO asset_placement(project_id,clip_key,asset_id,role,volume,fade_in_ms,fade_out_ms,visual_x,visual_y,visual_scale,created_at) SELECT ?,p.clip_key,p.asset_id,p.role,p.volume,p.fade_in_ms,p.fade_out_ms,p.visual_x,p.visual_y,p.visual_scale,? FROM asset_placement p WHERE p.project_id=? AND p.clip_key IN (SELECT clip_key FROM revision_clip WHERE revision_id=?)",
                new Object[]{targetProjectId, System.currentTimeMillis(), sourceProjectId, revisionId});
    }

    void writePlacementsJson(SQLiteDatabase db, long projectId, JSONObject root) throws JSONException {
        JSONArray placements = new JSONArray();
        String sql = "SELECT p.clip_key,a.uri,a.name,a.media_type,a.tags,p.role,p.volume,p.fade_in_ms,p.fade_out_ms,p.visual_x,p.visual_y,p.visual_scale FROM asset_placement p JOIN asset a ON a.id=p.asset_id WHERE p.project_id=? ORDER BY p.created_at";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(projectId)})) {
            while (c.moveToNext()) {
                JSONObject value = new JSONObject();
                value.put("clipKey", c.getString(0));
                value.put("uri", c.getString(1));
                value.put("name", c.getString(2));
                value.put("mediaType", c.getString(3));
                value.put("tags", c.getString(4));
                value.put("role", c.getString(5));
                value.put("volume", c.getDouble(6));
                value.put("fadeInMs", c.getLong(7));
                value.put("fadeOutMs", c.getLong(8));
                value.put("visualX", c.getDouble(9));
                value.put("visualY", c.getDouble(10));
                value.put("visualScale", c.getDouble(11));
                placements.put(value);
            }
        }
        root.put("placements", placements);
    }

    void importPlacements(SQLiteDatabase db, long projectId, JSONArray placements) throws JSONException {
        if (placements == null || placements.length() > 20000) return;
        for (int i = 0; i < placements.length(); i++) {
            JSONObject value = placements.getJSONObject(i);
            ContentValues asset = new ContentValues();
            asset.put("uri", value.getString("uri"));
            asset.put("name", value.optString("name", "恢复素材"));
            asset.put("media_type", value.optString("mediaType", "application/octet-stream"));
            asset.put("tags", value.optString("tags", ""));
            asset.put("created_at", System.currentTimeMillis());
            long assetId = db.insertWithOnConflict("asset", null, asset, SQLiteDatabase.CONFLICT_IGNORE);
            if (assetId < 0) {
                try (Cursor c = db.query("asset", new String[]{"id"}, "uri=?", new String[]{value.getString("uri")}, null, null, null)) {
                    if (c.moveToFirst()) assetId = c.getLong(0);
                }
            }
            if (assetId >= 0) {
                ContentValues p = new ContentValues();
                p.put("project_id", projectId);
                p.put("clip_key", value.getString("clipKey"));
                p.put("asset_id", assetId);
                p.put("role", value.optString("role", "OVERLAY"));
                p.put("volume", value.optDouble("volume", 1));
                p.put("fade_in_ms", value.optLong("fadeInMs", 0));
                p.put("fade_out_ms", value.optLong("fadeOutMs", 0));
                p.put("visual_x", value.optDouble("visualX", .68));
                p.put("visual_y", value.optDouble("visualY", .66));
                p.put("visual_scale", value.optDouble("visualScale", .31));
                p.put("created_at", System.currentTimeMillis());
                db.insertWithOnConflict("asset_placement", null, p, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }
}
