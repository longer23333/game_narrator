package cn.longer233.gamenarrator.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-scoped read/write: projects, timeline clips, revisions, subtitles,
 * track states, per-clip configs, effect templates, compilations and archives.
 */
public final class ProjectRepository {
    public static final class ProjectInfo {
        private final long id, updatedAt;
        private final String name, status;
        private final String gameCategory, editingScope;
        private final int clipCount;
        ProjectInfo(long id, String name, String status, long updatedAt, int clipCount,
                    String gameCategory, String editingScope) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.updatedAt = updatedAt;
            this.clipCount = clipCount;
            this.gameCategory = gameCategory == null ? "" : gameCategory;
            this.editingScope = editingScope == null ? "" : editingScope;
        }
        public long id() { return id; }
        public String name() { return name; }
        public String status() { return status; }
        public long updatedAt() { return updatedAt; }
        public int clipCount() { return clipCount; }
        public String gameCategory() { return gameCategory; }
        public String editingScope() { return editingScope; }
    }

    public static final class TaskBrief {
        private final String gameCategory, commentaryStyle, editingScope, brief, terminologyGlossary;
        private final int targetDurationSeconds;
        private final boolean storyboardReviewEnabled, automaticGenerationEnabled;

        public TaskBrief(String gameCategory, String commentaryStyle, String editingScope, int targetDurationSeconds,
                         String brief, String terminologyGlossary, boolean storyboardReviewEnabled,
                         boolean automaticGenerationEnabled) {
            this.gameCategory = gameCategory == null ? "ACTION" : gameCategory;
            this.commentaryStyle = commentaryStyle == null ? "ANIME_THEATER" : commentaryStyle;
            this.editingScope = editingScope == null ? "FULL_VIDEO" : editingScope;
            this.targetDurationSeconds = Math.max(0, targetDurationSeconds);
            this.brief = brief == null ? "" : brief;
            this.terminologyGlossary = terminologyGlossary == null ? "" : terminologyGlossary;
            this.storyboardReviewEnabled = storyboardReviewEnabled;
            this.automaticGenerationEnabled = automaticGenerationEnabled;
        }

        public String gameCategory() { return gameCategory; }
        public String commentaryStyle() { return commentaryStyle; }
        public String editingScope() { return editingScope; }
        public int targetDurationSeconds() { return targetDurationSeconds; }
        public String brief() { return brief; }
        public String terminologyGlossary() { return terminologyGlossary; }
        public boolean storyboardReviewEnabled() { return storyboardReviewEnabled; }
        public boolean automaticGenerationEnabled() { return automaticGenerationEnabled; }
    }

    public static final class RevisionInfo {
        private final long id, createdAt;
        private final String reason;
        private final int clipCount;
        RevisionInfo(long id, long createdAt, String reason, int clipCount) {
            this.id = id;
            this.createdAt = createdAt;
            this.reason = reason;
            this.clipCount = clipCount;
        }
        public long id() { return id; }
        public long createdAt() { return createdAt; }
        public String reason() { return reason; }
        public int clipCount() { return clipCount; }
    }

    public static final class TrackState {
        private final boolean muted, solo;
        TrackState(boolean muted, boolean solo) {
            this.muted = muted;
            this.solo = solo;
        }
        public boolean muted() { return muted; }
        public boolean solo() { return solo; }
    }

    public static final class SubtitleCueInfo {
        private final long id, startMs, endMs;
        private final String text;
        SubtitleCueInfo(long id, long startMs, long endMs, String text) {
            this.id = id;
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
        public long id() { return id; }
        public long startMs() { return startMs; }
        public long endMs() { return endMs; }
        public String text() { return text; }
    }

    public static final class ClipReviewInfo {
        private final String clipKey, status, note;
        private final long updatedAt;
        ClipReviewInfo(String clipKey, String status, String note, long updatedAt) {
            this.clipKey = clipKey;
            this.status = status;
            this.note = note;
            this.updatedAt = updatedAt;
        }
        public String clipKey() { return clipKey; }
        public String status() { return status; }
        public String note() { return note; }
        public long updatedAt() { return updatedAt; }
    }

    public static final class VoiceConfig {
        private final String voiceName;
        private final float speed, pitch;
        VoiceConfig(String voiceName, float speed, float pitch) {
            this.voiceName = voiceName;
            this.speed = speed;
            this.pitch = pitch;
        }
        public String voiceName() { return voiceName; }
        public float speed() { return speed; }
        public float pitch() { return pitch; }
    }

    public static final class ClipAudioConfig {
        private final float volume;
        private final long fadeInMs, fadeOutMs;
        ClipAudioConfig(float volume, long fadeInMs, long fadeOutMs) {
            this.volume = volume;
            this.fadeInMs = fadeInMs;
            this.fadeOutMs = fadeOutMs;
        }
        public float volume() { return volume; }
        public long fadeInMs() { return fadeInMs; }
        public long fadeOutMs() { return fadeOutMs; }
    }

    public static final class ClipVisualConfig {
        private final float brightness, contrast, saturation, temperature, hue, scale, rotation;
        ClipVisualConfig(float brightness, float contrast, float saturation, float temperature, float hue,
                         float scale, float rotation) {
            this.brightness = brightness;
            this.contrast = contrast;
            this.saturation = saturation;
            this.temperature = temperature;
            this.hue = hue;
            this.scale = scale;
            this.rotation = rotation;
        }
        public float brightness() { return brightness; }
        public float contrast() { return contrast; }
        public float saturation() { return saturation; }
        public float temperature() { return temperature; }
        public float hue() { return hue; }
        public float scale() { return scale; }
        public float rotation() { return rotation; }
    }

    public static final class KeyframeInfo implements KeyframeInterpolator.Point {
        private final long timeMs;
        private final float value;
        private final String easing;
        KeyframeInfo(long timeMs, float value) {
            this(timeMs, value, KeyframeEasing.LINEAR);
        }
        KeyframeInfo(long timeMs, float value, String easing) {
            this.timeMs = timeMs;
            this.value = value;
            this.easing = KeyframeEasing.isSupported(easing) ? easing : KeyframeEasing.LINEAR;
        }
        public long timeMs() { return timeMs; }
        public float value() { return value; }
        public String easing() { return easing; }
    }

    public static final class EffectTemplateInfo {
        private final long id, createdAt;
        private final String name, cue;
        EffectTemplateInfo(long id, String name, String cue, long createdAt) {
            this.id = id;
            this.name = name;
            this.cue = cue;
            this.createdAt = createdAt;
        }
        public long id() { return id; }
        public String name() { return name; }
        public String cue() { return cue; }
        public long createdAt() { return createdAt; }
    }

    public static final class CompilationInfo {
        private final long id, createdAt;
        private final String name;
        private final int itemCount;
        CompilationInfo(long id, String name, long createdAt, int itemCount) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.itemCount = itemCount;
        }
        public long id() { return id; }
        public String name() { return name; }
        public long createdAt() { return createdAt; }
        public int itemCount() { return itemCount; }
    }

    public static final class CompilationItemInfo {
        private final long id;
        private final String projectName, clipName, clipKey;
        private final TimelineClip clip;
        CompilationItemInfo(long id, String projectName, String clipName, String clipKey, TimelineClip clip) {
            this.id = id;
            this.projectName = projectName;
            this.clipName = clipName;
            this.clipKey = clipKey;
            this.clip = clip;
        }
        public long id() { return id; }
        public String projectName() { return projectName; }
        public String clipName() { return clipName; }
        public String clipKey() { return clipKey; }
        public TimelineClip clip() { return clip; }
    }

    private final MobileDatabase database;
    private final MobileAssetStore assets;
    private final MobileExportStore exports;
    private long activeProjectId;

    public ProjectRepository(MobileDatabase database, MobileAssetStore assets, MobileExportStore exports) {
        this.database = database;
        this.assets = assets;
        this.exports = exports;
        activeProjectId = ProjectMigration.readActiveProjectId(database.getWritableDatabase());
        recoverInterruptedProjects();
    }

    public long activeProjectId() { return activeProjectId; }

    public long createProject(String name) {
        SQLiteDatabase db = database.getWritableDatabase();
        long id = ProjectMigration.insertProject(db, name);
        selectProject(id);
        return id;
    }

    public void selectProject(long id) {
        activeProjectId = id;
        ProjectMigration.setActiveProjectId(database.getWritableDatabase(), id);
    }

    public String pipelineStage() { return pipelineStageFor(activeProjectId); }

    public String pipelineStageFor(long projectId) {
        try (Cursor c = database.getReadableDatabase().query("project", new String[]{"pipeline_stage"},
                "id=?", new String[]{String.valueOf(projectId)}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : PipelineStages.IMPORT;
        } catch (Exception ignored) {
            return PipelineStages.IMPORT;
        }
    }

    public void setPipelineStage(String stage) {
        if (PipelineStages.index(stage) < 0) throw new IllegalArgumentException("不支持的流水线阶段");
        ContentValues row = new ContentValues();
        row.put("pipeline_stage", stage);
        row.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update("project", row, "id=?", new String[]{String.valueOf(activeProjectId)});
    }

    public String activeProjectStatus() {
        try (Cursor c = database.getReadableDatabase().query("project", new String[]{"status"},
                "id=?", new String[]{String.valueOf(activeProjectId)}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : "DRAFT";
        } catch (Exception ignored) {
            return "DRAFT";
        }
    }

    public String activeProjectName() {
        try (Cursor c = database.getReadableDatabase().query("project", new String[]{"name"},
                "id=?", new String[]{String.valueOf(activeProjectId)}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : "未命名项目";
        }
    }

    public List<ProjectInfo> listProjects() {
        List<ProjectInfo> out = new ArrayList<>();
        String sql = "SELECT p.id,p.name,p.status,p.updated_at,COUNT(c.id),p.game_category,p.editing_scope FROM project p LEFT JOIN timeline_clip c ON c.project_id=p.id WHERE p.archived=0 GROUP BY p.id ORDER BY p.updated_at DESC";
        try (Cursor c = database.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                out.add(new ProjectInfo(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4),
                        c.getString(5), c.getString(6)));
            }
        }
        return out;
    }

    public List<ProjectInfo> listArchivedProjects() {
        List<ProjectInfo> out = new ArrayList<>();
        String sql = "SELECT p.id,p.name,p.status,p.updated_at,COUNT(c.id),p.game_category,p.editing_scope FROM project p LEFT JOIN timeline_clip c ON c.project_id=p.id WHERE p.archived=1 GROUP BY p.id ORDER BY p.updated_at DESC";
        try (Cursor c = database.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                out.add(new ProjectInfo(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4),
                        c.getString(5), c.getString(6)));
            }
        }
        return out;
    }

    public TaskBrief taskBrief() {
        try (Cursor c = database.getReadableDatabase().query("project",
                new String[]{"game_category", "commentary_style", "editing_scope", "target_duration_seconds",
                        "brief", "terminology_glossary", "storyboard_review_enabled", "automatic_generation_enabled"},
                "id=?", new String[]{String.valueOf(activeProjectId)}, null, null, null)) {
            if (c.moveToFirst()) {
                return new TaskBrief(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4),
                        c.getString(5), c.getInt(6) != 0, c.getInt(7) != 0);
            }
        }
        return new TaskBrief("ACTION", "ANIME_THEATER", "FULL_VIDEO", 0, "", "", true, true);
    }

    public void updateTaskBrief(TaskBrief brief) {
        if (!java.util.Set.of("ACTION", "STORY", "RPG", "ANIME_GAME").contains(brief.gameCategory())) {
            throw new IllegalArgumentException("不支持的内容类别");
        }
        if (!java.util.Set.of("ANIME_THEATER", "PASSIONATE", "HUMOROUS").contains(brief.commentaryStyle())) {
            throw new IllegalArgumentException("不支持的解说风格");
        }
        if (!java.util.Set.of("FULL_VIDEO", "HIGHLIGHTS").contains(brief.editingScope())) {
            throw new IllegalArgumentException("不支持的剪辑范围");
        }
        if (brief.targetDurationSeconds() > 3600) throw new IllegalArgumentException("目标时长不能超过 3600 秒");
        if (brief.brief().length() > 500) throw new IllegalArgumentException("创作要求不能超过 500 个字符");
        if (brief.terminologyGlossary().length() > 4000) throw new IllegalArgumentException("术语纠错词表不能超过 4000 个字符");
        ContentValues row = new ContentValues();
        row.put("game_category", brief.gameCategory());
        row.put("commentary_style", brief.commentaryStyle());
        row.put("editing_scope", brief.editingScope());
        row.put("target_duration_seconds", brief.targetDurationSeconds());
        row.put("brief", brief.brief());
        row.put("terminology_glossary", brief.terminologyGlossary());
        row.put("storyboard_review_enabled", brief.storyboardReviewEnabled() ? 1 : 0);
        row.put("automatic_generation_enabled", brief.automaticGenerationEnabled() ? 1 : 0);
        database.getWritableDatabase().update("project", row, "id=?", new String[]{String.valueOf(activeProjectId)});
    }

    public void renameProject(long id, String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank() || value.length() > 120) throw new IllegalArgumentException("项目名称长度必须为 1 到 120 个字符");
        ContentValues row = new ContentValues();
        row.put("name", value);
        row.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update("project", row, "id=?", new String[]{String.valueOf(id)});
    }

    public long duplicateProject(long id) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        long copy = -1;
        try {
            String name;
            try (Cursor c = db.query("project", new String[]{"name"}, "id=? AND archived=0",
                    new String[]{String.valueOf(id)}, null, null, null)) {
                if (!c.moveToFirst()) throw new IllegalArgumentException("项目不存在");
                name = c.getString(0);
            }
            copy = ProjectMigration.insertProject(db, name + " · 副本");
            db.execSQL("INSERT INTO timeline_clip(project_id,position,clip_key,uri,name,start_ms,end_ms,muted,subtitle,narration,effect_cue,track) SELECT ?,position,clip_key,uri,name,start_ms,end_ms,muted,subtitle,narration,effect_cue,track FROM timeline_clip WHERE project_id=?",
                    new Object[]{copy, id});
            assets.duplicatePlacementsForProject(db, copy, id);
            db.execSQL("INSERT INTO project_track_state(project_id,track_type,muted,solo) SELECT ?,track_type,muted,solo FROM project_track_state WHERE project_id=?",
                    new Object[]{copy, id});
            db.execSQL("INSERT INTO project_subtitle_cue(project_id,start_ms,end_ms,text,position) SELECT ?,start_ms,end_ms,text,position FROM project_subtitle_cue WHERE project_id=?",
                    new Object[]{copy, id});
            db.execSQL("INSERT INTO project_clip_review(project_id,clip_key,status,note,updated_at) SELECT ?,clip_key,status,note,updated_at FROM project_clip_review WHERE project_id=?",
                    new Object[]{copy, id});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return copy;
    }

    public void archiveProject(long id) {
        ContentValues row = new ContentValues();
        row.put("archived", 1);
        row.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update("project", row, "id=?", new String[]{String.valueOf(id)});
        if (activeProjectId == id) selectFallbackProject();
    }

    public void restoreProject(long id) {
        ContentValues row = new ContentValues();
        row.put("archived", 0);
        row.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update("project", row, "id=?", new String[]{String.valueOf(id)});
    }

    public void permanentlyDeleteProject(long id) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("clip_compilation_item", "project_id=?", new String[]{String.valueOf(id)});
            assets.deletePlacementsForProject(db, id);
            db.delete("project_track_state", "project_id=?", new String[]{String.valueOf(id)});
            db.delete("project_subtitle_cue", "project_id=?", new String[]{String.valueOf(id)});
            db.delete("project_clip_review", "project_id=?", new String[]{String.valueOf(id)});
            exports.deleteExportsForProject(db, id);
            try (Cursor c = db.query("project_revision", new String[]{"id"}, "project_id=?",
                    new String[]{String.valueOf(id)}, null, null, null)) {
                while (c.moveToNext()) db.delete("revision_clip", "revision_id=?", new String[]{String.valueOf(c.getLong(0))});
            }
            db.delete("project_revision", "project_id=?", new String[]{String.valueOf(id)});
            db.delete("timeline_clip", "project_id=?", new String[]{String.valueOf(id)});
            db.delete("project", "id=? AND archived=1", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (activeProjectId == id) selectFallbackProject();
    }

    private void selectFallbackProject() {
        try (Cursor c = database.getReadableDatabase().query("project", new String[]{"id"}, "archived=0",
                null, null, null, "updated_at DESC", "1")) {
            if (c.moveToFirst()) {
                selectProject(c.getLong(0));
                return;
            }
        }
        selectProject(ProjectMigration.insertProject(database.getWritableDatabase(), "未命名项目"));
    }

    public void updateStatus(String status) {
        ContentValues row = new ContentValues();
        row.put("status", status);
        row.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update("project", row, "id=?", new String[]{String.valueOf(activeProjectId)});
    }

    public TrackState trackState(String type) {
        try (Cursor c = database.getReadableDatabase().query("project_track_state", new String[]{"muted", "solo"},
                "project_id=? AND track_type=?", new String[]{String.valueOf(activeProjectId), type}, null, null, null)) {
            return c.moveToFirst() ? new TrackState(c.getInt(0) != 0, c.getInt(1) != 0) : new TrackState(false, false);
        }
    }

    public void updateTrackState(String type, boolean muted, boolean solo) {
        ContentValues row = new ContentValues();
        row.put("project_id", activeProjectId);
        row.put("track_type", type);
        row.put("muted", muted ? 1 : 0);
        row.put("solo", solo ? 1 : 0);
        database.getWritableDatabase().insertWithOnConflict("project_track_state", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public VoiceConfig voiceConfig(String clipKey) {
        try (Cursor c = database.getReadableDatabase().query("project_clip_voice", new String[]{"voice_name", "speed", "pitch"},
                "project_id=? AND clip_key=?", new String[]{String.valueOf(activeProjectId), clipKey}, null, null, null)) {
            return c.moveToFirst() ? new VoiceConfig(c.getString(0), c.getFloat(1), c.getFloat(2)) : new VoiceConfig("", 1f, 1f);
        }
    }

    public void saveVoiceConfig(String clipKey, String voiceName, float speed, float pitch) {
        ContentValues row = new ContentValues();
        row.put("project_id", activeProjectId);
        row.put("clip_key", clipKey);
        row.put("voice_name", voiceName == null ? "" : voiceName);
        row.put("speed", Math.max(.5f, Math.min(2f, speed)));
        row.put("pitch", Math.max(.5f, Math.min(2f, pitch)));
        database.getWritableDatabase().insertWithOnConflict("project_clip_voice", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public ClipAudioConfig clipAudioConfig(String clipKey) {
        try (Cursor c = database.getReadableDatabase().query("project_clip_audio", new String[]{"volume", "fade_in_ms", "fade_out_ms"},
                "project_id=? AND clip_key=?", new String[]{String.valueOf(activeProjectId), clipKey}, null, null, null)) {
            return c.moveToFirst() ? new ClipAudioConfig(c.getFloat(0), c.getLong(1), c.getLong(2)) : new ClipAudioConfig(1f, 0, 0);
        }
    }

    public void saveClipAudioConfig(String clipKey, float volume, long fadeInMs, long fadeOutMs) {
        ContentValues row = new ContentValues();
        row.put("project_id", activeProjectId);
        row.put("clip_key", clipKey);
        row.put("volume", Math.max(0f, Math.min(2f, volume)));
        row.put("fade_in_ms", Math.max(0, fadeInMs));
        row.put("fade_out_ms", Math.max(0, fadeOutMs));
        database.getWritableDatabase().insertWithOnConflict("project_clip_audio", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public ClipVisualConfig clipVisualConfig(String clipKey) {
        try (Cursor c = database.getReadableDatabase().query("project_clip_visual",
                new String[]{"brightness", "contrast", "saturation", "temperature", "hue", "scale", "rotation"},
                "project_id=? AND clip_key=?", new String[]{String.valueOf(activeProjectId), clipKey}, null, null, null)) {
            return c.moveToFirst() ? new ClipVisualConfig(c.getFloat(0), c.getFloat(1), c.getFloat(2), c.getFloat(3),
                    c.getFloat(4), c.getFloat(5), c.getFloat(6)) : new ClipVisualConfig(0, 0, 0, 0, 0, 1, 0);
        }
    }

    public void saveClipVisualConfig(String clipKey, float brightness, float contrast, float saturation,
                                     float temperature, float hue, float scale, float rotation) {
        ContentValues row = new ContentValues();
        row.put("project_id", activeProjectId);
        row.put("clip_key", clipKey);
        row.put("brightness", Math.max(-1f, Math.min(1f, brightness)));
        row.put("contrast", Math.max(-1f, Math.min(1f, contrast)));
        row.put("saturation", Math.max(-100f, Math.min(100f, saturation)));
        row.put("temperature", Math.max(-100f, Math.min(100f, temperature)));
        row.put("hue", Math.max(-180f, Math.min(180f, hue)));
        row.put("scale", Math.max(.25f, Math.min(3f, scale)));
        row.put("rotation", Math.max(-180f, Math.min(180f, rotation)));
        database.getWritableDatabase().insertWithOnConflict("project_clip_visual", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<KeyframeInfo> listKeyframes(String clipKey, String property) {
        List<KeyframeInfo> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query("project_clip_keyframe", new String[]{"time_ms", "value", "easing"},
                "project_id=? AND clip_key=? AND property=?", new String[]{String.valueOf(activeProjectId), clipKey, property},
                null, null, "time_ms")) {
            while (c.moveToNext()) out.add(new KeyframeInfo(c.getLong(0), c.getFloat(1), c.getString(2)));
        }
        return out;
    }

    public void saveKeyframe(String clipKey, String property, long timeMs, float value) {
        saveKeyframe(clipKey, property, timeMs, value, KeyframeEasing.LINEAR);
    }

    public void saveKeyframe(String clipKey, String property, long timeMs, float value, String easing) {
        if (!java.util.Set.of("scale", "rotation", "x", "y", "opacity", "volume").contains(property)) {
            throw new IllegalArgumentException("不支持的关键帧属性");
        }
        if (!KeyframeEasing.isSupported(easing)) throw new IllegalArgumentException("不支持的关键帧曲线");
        ContentValues row = new ContentValues();
        row.put("project_id", activeProjectId);
        row.put("clip_key", clipKey);
        row.put("property", property);
        row.put("time_ms", Math.max(0, timeMs));
        row.put("value", value);
        row.put("easing", easing);
        database.getWritableDatabase().insertWithOnConflict("project_clip_keyframe", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void replaceKeyframes(String clipKey, String property, List<KeyframeInfo> frames) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            clearKeyframes(clipKey, property);
            if (frames != null) {
                for (KeyframeInfo frame : frames) {
                    ContentValues row = new ContentValues();
                    row.put("project_id", activeProjectId);
                    row.put("clip_key", clipKey);
                    row.put("property", property);
                    row.put("time_ms", Math.max(0, frame.timeMs()));
                    row.put("value", frame.value());
                    row.put("easing", frame.easing());
                    db.insertWithOnConflict("project_clip_keyframe", null, row, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearKeyframes(String clipKey, String property) {
        database.getWritableDatabase().delete("project_clip_keyframe", "project_id=? AND clip_key=? AND property=?",
                new String[]{String.valueOf(activeProjectId), clipKey, property});
    }

    public long createEffectTemplate(String name, String cue) {
        String cleanName = EffectCueUtil.clean(name);
        if (!EffectCueUtil.isValidName(cleanName)) throw new IllegalArgumentException("模板名称必须为 1 到 40 个字符");
        String cleanCue = EffectCueUtil.clean(cue);
        if (cleanCue.isBlank()) throw new IllegalArgumentException("模板特效提示不能为空");
        ContentValues row = new ContentValues();
        row.put("name", cleanName);
        row.put("cue", cleanCue);
        row.put("created_at", System.currentTimeMillis());
        return database.getWritableDatabase().insertOrThrow("effect_template", null, row);
    }

    public List<EffectTemplateInfo> listEffectTemplates() {
        List<EffectTemplateInfo> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query("effect_template", new String[]{"id", "name", "cue", "created_at"},
                null, null, null, null, "created_at DESC,id DESC")) {
            while (c.moveToNext()) out.add(new EffectTemplateInfo(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        return out;
    }

    public void deleteEffectTemplate(long id) {
        database.getWritableDatabase().delete("effect_template", "id=?", new String[]{String.valueOf(id)});
    }

    public String exportEffectTemplatesJson() throws org.json.JSONException {
        return ProjectSerializer.templatesToJson(listEffectTemplates());
    }

    public int importEffectTemplates(String json) throws org.json.JSONException {
        List<ProjectSerializer.TemplateDraft> drafts = ProjectSerializer.templatesFromJson(json);
        int imported = 0;
        for (ProjectSerializer.TemplateDraft draft : drafts) {
            String name = EffectCueUtil.clean(draft.name());
            if (!EffectCueUtil.isValidName(name)) continue;
            if (EffectCueUtil.clean(draft.cue()).isBlank()) continue;
            createEffectTemplate(name, draft.cue());
            imported++;
        }
        return imported;
    }

    public long createCompilation(String name) {
        ContentValues row = new ContentValues();
        row.put("name", name.trim());
        row.put("created_at", System.currentTimeMillis());
        return database.getWritableDatabase().insertOrThrow("clip_compilation", null, row);
    }

    public List<CompilationInfo> listCompilations() {
        List<CompilationInfo> out = new ArrayList<>();
        String sql = "SELECT c.id,c.name,c.created_at,COUNT(i.id) FROM clip_compilation c LEFT JOIN clip_compilation_item i ON i.compilation_id=c.id GROUP BY c.id ORDER BY c.created_at DESC";
        try (Cursor c = database.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) out.add(new CompilationInfo(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3)));
        }
        return out;
    }

    public void addCompilationItem(long compilationId, String clipKey) {
        SQLiteDatabase db = database.getWritableDatabase();
        int position = 0;
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM clip_compilation_item WHERE compilation_id=?",
                new String[]{String.valueOf(compilationId)})) {
            if (c.moveToFirst()) position = c.getInt(0);
        }
        ContentValues row = new ContentValues();
        row.put("compilation_id", compilationId);
        row.put("project_id", activeProjectId);
        row.put("clip_key", clipKey);
        row.put("position", position);
        row.put("created_at", System.currentTimeMillis());
        db.insertWithOnConflict("clip_compilation_item", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<CompilationItemInfo> listCompilationItems(long compilationId) {
        List<CompilationItemInfo> out = new ArrayList<>();
        String sql = "SELECT i.id,p.name,t.name,t.clip_key,t.uri,t.start_ms,t.end_ms,t.muted,t.subtitle,t.narration,t.effect_cue FROM clip_compilation_item i JOIN project p ON p.id=i.project_id JOIN timeline_clip t ON t.project_id=i.project_id AND t.clip_key=i.clip_key WHERE i.compilation_id=? ORDER BY i.position";
        try (Cursor c = database.getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(compilationId)})) {
            while (c.moveToNext()) {
                TimelineClip clip = new TimelineClip(c.getString(3), Uri.parse(c.getString(4)), c.getString(2), c.getLong(5), c.getLong(6));
                clip.update(c.getLong(5), c.getLong(6), c.getInt(7) != 0, c.getString(8));
                clip.updateCreativeText(c.getString(8), c.getString(9), c.getString(10));
                out.add(new CompilationItemInfo(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), clip));
            }
        }
        return out;
    }

    public void removeCompilationItem(long itemId) {
        database.getWritableDatabase().delete("clip_compilation_item", "id=?", new String[]{String.valueOf(itemId)});
    }

    public void deleteCompilation(long id) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("clip_compilation_item", "compilation_id=?", new String[]{String.valueOf(id)});
            db.delete("clip_compilation", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void moveCompilationItem(long compilationId, long itemId, int delta) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            long otherId = -1;
            int position = -1;
            try (Cursor c = db.rawQuery("SELECT position FROM clip_compilation_item WHERE id=? AND compilation_id=?",
                    new String[]{String.valueOf(itemId), String.valueOf(compilationId)})) {
                if (c.moveToFirst()) position = c.getInt(0);
            }
            int target = position + delta;
            if (position < 0 || target < 0) return;
            try (Cursor c = db.rawQuery("SELECT id FROM clip_compilation_item WHERE compilation_id=? AND position=?",
                    new String[]{String.valueOf(compilationId), String.valueOf(target)})) {
                if (c.moveToFirst()) otherId = c.getLong(0);
            }
            if (otherId < 0) return;
            db.execSQL("UPDATE clip_compilation_item SET position=-1 WHERE id=?", new Object[]{itemId});
            db.execSQL("UPDATE clip_compilation_item SET position=? WHERE id=?", new Object[]{position, otherId});
            db.execSQL("UPDATE clip_compilation_item SET position=? WHERE id=?", new Object[]{target, itemId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<TimelineClip> loadClips() {
        return readClips("timeline_clip", "project_id=?", new String[]{String.valueOf(activeProjectId)});
    }

    public List<SubtitleCueInfo> listSubtitleCues() {
        List<SubtitleCueInfo> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query("project_subtitle_cue", new String[]{"id", "start_ms", "end_ms", "text"},
                "project_id=?", new String[]{String.valueOf(activeProjectId)}, null, null, "position,start_ms")) {
            while (c.moveToNext()) out.add(new SubtitleCueInfo(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3)));
        }
        return out;
    }

    public ClipReviewInfo clipReview(String clipKey) {
        try (Cursor c = database.getReadableDatabase().query("project_clip_review", new String[]{"status", "note", "updated_at"},
                "project_id=? AND clip_key=?", new String[]{String.valueOf(activeProjectId), clipKey}, null, null, null)) {
            return c.moveToFirst() ? new ClipReviewInfo(clipKey, c.getString(0), c.getString(1), c.getLong(2))
                    : new ClipReviewInfo(clipKey, "PENDING", "", 0);
        }
    }

    public List<ClipReviewInfo> listClipReviews() {
        List<ClipReviewInfo> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query("project_clip_review", new String[]{"clip_key", "status", "note", "updated_at"},
                "project_id=?", new String[]{String.valueOf(activeProjectId)}, null, null, "updated_at DESC")) {
            while (c.moveToNext()) out.add(new ClipReviewInfo(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        return out;
    }

    public void reviewClip(String clipKey, String status, String note) {
        if (!"APPROVED".equals(status) && !"NEEDS_CHANGES".equals(status)) throw new IllegalArgumentException("评审状态无效");
        String clean = note == null ? "" : note.trim();
        if (clean.length() > 500) throw new IllegalArgumentException("评审备注不能超过 500 个字符");
        ContentValues row = new ContentValues();
        row.put("project_id", activeProjectId);
        row.put("clip_key", clipKey);
        row.put("status", status);
        row.put("note", clean);
        row.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().insertWithOnConflict("project_clip_review", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void clearClipReview(String clipKey) {
        database.getWritableDatabase().delete("project_clip_review", "project_id=? AND clip_key=?",
                new String[]{String.valueOf(activeProjectId), clipKey});
    }

    public void replaceSubtitleCues(List<SubtitleFileCodec.Cue> cues) {
        if (cues == null || cues.size() > 10000) throw new IllegalArgumentException("字幕条目不能超过 10000 条");
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("project_subtitle_cue", "project_id=?", new String[]{String.valueOf(activeProjectId)});
            for (int i = 0; i < cues.size(); i++) {
                SubtitleFileCodec.Cue cue = cues.get(i);
                ContentValues row = new ContentValues();
                row.put("project_id", activeProjectId);
                row.put("start_ms", cue.startMs());
                row.put("end_ms", cue.endMs());
                row.put("text", cue.text());
                row.put("position", i);
                db.insertOrThrow("project_subtitle_cue", null, row);
            }
            ContentValues project = new ContentValues();
            project.put("updated_at", System.currentTimeMillis());
            db.update("project", project, "id=?", new String[]{String.valueOf(activeProjectId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearSubtitleCues() {
        replaceSubtitleCues(java.util.Collections.emptyList());
    }

    public List<TimelineClip> loadRevision(long id) {
        return readClips("revision_clip", "revision_id=?", new String[]{String.valueOf(id)});
    }

    private List<TimelineClip> readClips(String table, String selection, String[] args) {
        List<TimelineClip> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query(table,
                new String[]{"clip_key", "uri", "name", "start_ms", "end_ms", "muted", "subtitle", "narration", "effect_cue", "track"},
                selection, args, null, null, "position ASC")) {
            while (c.moveToNext()) {
                TimelineClip clip = new TimelineClip(c.getString(0), Uri.parse(c.getString(1)), c.getString(2), c.getLong(3), c.getLong(4));
                clip.setTrack(c.getString(9));
                clip.update(c.getLong(3), c.getLong(4), c.getInt(5) != 0, c.getString(6));
                clip.updateCreativeText(c.getString(6), c.getString(7), c.getString(8));
                out.add(clip);
            }
        }
        return out;
    }

    public List<RevisionInfo> listRevisions() {
        List<RevisionInfo> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query("project_revision", new String[]{"id", "created_at", "reason", "clip_count"},
                "project_id=?", new String[]{String.valueOf(activeProjectId)}, null, null, "created_at DESC", "100")) {
            while (c.moveToNext()) out.add(new RevisionInfo(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3)));
        }
        return out;
    }

    public void renameRevision(long id, String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank() || value.length() > 120) throw new IllegalArgumentException("版本名称长度必须为 1 到 120 个字符");
        ContentValues row = new ContentValues();
        row.put("reason", value);
        database.getWritableDatabase().update("project_revision", row, "id=? AND project_id=?",
                new String[]{String.valueOf(id), String.valueOf(activeProjectId)});
    }

    public long forkRevision(long revisionId, String name) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        long forkId = -1;
        try {
            long sourceProject = -1;
            try (Cursor c = db.query("project_revision", new String[]{"project_id"}, "id=? AND project_id=?",
                    new String[]{String.valueOf(revisionId), String.valueOf(activeProjectId)}, null, null, null)) {
                if (c.moveToFirst()) sourceProject = c.getLong(0);
            }
            if (sourceProject < 0) throw new IllegalArgumentException("版本不存在");
            String forkName = name == null || name.trim().isBlank() ? activeProjectName() + " · 分支" : name.trim();
            forkId = ProjectMigration.insertProject(db, forkName);
            db.execSQL("INSERT INTO timeline_clip(project_id,position,clip_key,uri,name,start_ms,end_ms,muted,subtitle,narration,effect_cue,track) SELECT ?,position,clip_key,uri,name,start_ms,end_ms,muted,subtitle,narration,effect_cue,track FROM revision_clip WHERE revision_id=? ORDER BY position",
                    new Object[]{forkId, revisionId});
            assets.forkPlacementsForProject(db, forkId, sourceProject, revisionId);
            db.execSQL("INSERT INTO project_track_state(project_id,track_type,muted,solo) SELECT ?,track_type,muted,solo FROM project_track_state WHERE project_id=?",
                    new Object[]{forkId, sourceProject});
            db.execSQL("INSERT INTO project_subtitle_cue(project_id,start_ms,end_ms,text,position) SELECT ?,start_ms,end_ms,text,position FROM project_subtitle_cue WHERE project_id=?",
                    new Object[]{forkId, sourceProject});
            ContentValues revision = new ContentValues();
            revision.put("project_id", forkId);
            revision.put("created_at", System.currentTimeMillis());
            revision.put("reason", "分支起点");
            try (Cursor count = db.rawQuery("SELECT COUNT(*) FROM revision_clip WHERE revision_id=?",
                    new String[]{String.valueOf(revisionId)})) {
                revision.put("clip_count", count.moveToFirst() ? count.getInt(0) : 0);
            }
            long newRevision = db.insertOrThrow("project_revision", null, revision);
            db.execSQL("INSERT INTO revision_clip(revision_id,position,clip_key,uri,name,start_ms,end_ms,muted,subtitle,narration,effect_cue,track) SELECT ?,position,clip_key,uri,name,start_ms,end_ms,muted,subtitle,narration,effect_cue,track FROM revision_clip WHERE revision_id=?",
                    new Object[]{newRevision, revisionId});
            ProjectMigration.setActiveProjectId(db, forkId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        activeProjectId = forkId;
        return forkId;
    }

    public void saveClips(List<TimelineClip> clips, String reason) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("timeline_clip", "project_id=?", new String[]{String.valueOf(activeProjectId)});
            for (int i = 0; i < clips.size(); i++) {
                ContentValues row = clipValues(clips.get(i), i);
                row.put("project_id", activeProjectId);
                db.insertOrThrow("timeline_clip", null, row);
            }
            db.execSQL("DELETE FROM clip_compilation_item WHERE project_id=? AND clip_key NOT IN (SELECT clip_key FROM timeline_clip WHERE project_id=?)",
                    new Object[]{activeProjectId, activeProjectId});
            db.execSQL("DELETE FROM project_clip_review WHERE project_id=? AND clip_key NOT IN (SELECT clip_key FROM timeline_clip WHERE project_id=?)",
                    new Object[]{activeProjectId, activeProjectId});
            db.execSQL("DELETE FROM project_clip_voice WHERE project_id=? AND clip_key NOT IN (SELECT clip_key FROM timeline_clip WHERE project_id=?)",
                    new Object[]{activeProjectId, activeProjectId});
            db.execSQL("DELETE FROM project_clip_audio WHERE project_id=? AND clip_key NOT IN (SELECT clip_key FROM timeline_clip WHERE project_id=?)",
                    new Object[]{activeProjectId, activeProjectId});
            db.execSQL("DELETE FROM project_clip_visual WHERE project_id=? AND clip_key NOT IN (SELECT clip_key FROM timeline_clip WHERE project_id=?)",
                    new Object[]{activeProjectId, activeProjectId});
            db.execSQL("DELETE FROM project_clip_keyframe WHERE project_id=? AND clip_key NOT IN (SELECT clip_key FROM timeline_clip WHERE project_id=?)",
                    new Object[]{activeProjectId, activeProjectId});
            if (reason != null && !reason.isBlank()) createRevision(db, clips, reason);
            ContentValues project = new ContentValues();
            project.put("updated_at", System.currentTimeMillis());
            if (!clips.isEmpty()) project.put("status", "EDITING");
            db.update("project", project, "id=?", new String[]{String.valueOf(activeProjectId)});
            trimRevisions(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void createRevision(SQLiteDatabase db, List<TimelineClip> clips, String reason) {
        ContentValues rev = new ContentValues();
        rev.put("project_id", activeProjectId);
        rev.put("created_at", System.currentTimeMillis());
        rev.put("reason", reason);
        rev.put("clip_count", clips.size());
        long rid = db.insertOrThrow("project_revision", null, rev);
        for (int i = 0; i < clips.size(); i++) {
            ContentValues row = clipValues(clips.get(i), i);
            row.put("revision_id", rid);
            db.insertOrThrow("revision_clip", null, row);
        }
    }

    private static ContentValues clipValues(TimelineClip clip, int position) {
        ContentValues row = new ContentValues();
        row.put("position", position);
        row.put("clip_key", clip.key());
        row.put("uri", clip.uri().toString());
        row.put("name", clip.name());
        row.put("start_ms", clip.startMs());
        row.put("end_ms", clip.endMs());
        row.put("muted", clip.muted() ? 1 : 0);
        row.put("subtitle", clip.subtitle());
        row.put("narration", clip.narration());
        row.put("effect_cue", clip.effectCue());
        row.put("track", clip.track());
        return row;
    }

    public String exportActiveProject() throws org.json.JSONException {
        List<ProjectSerializer.RevisionArchive> revisions = new ArrayList<>();
        try (Cursor r = database.getReadableDatabase().query("project_revision",
                new String[]{"id", "created_at", "reason"}, "project_id=?",
                new String[]{String.valueOf(activeProjectId)}, null, null, "created_at")) {
            while (r.moveToNext()) {
                revisions.add(new ProjectSerializer.RevisionArchive(r.getLong(1), r.getString(2),
                        loadRevision(r.getLong(0))));
            }
        }
        Map<String, TrackState> tracks = new LinkedHashMap<>();
        for (String type : new String[]{"ORIGINAL", "NARRATION", "MUSIC"}) tracks.put(type, trackState(type));
        JSONObject root = ProjectSerializer.projectJson(activeProjectName(), loadClips(), listSubtitleCues(),
                listClipReviews(), revisions, tracks);
        assets.writePlacementsJson(database.getReadableDatabase(), activeProjectId, root);
        return root.toString(2);
    }

    public long importProject(String json) throws org.json.JSONException {
        JSONObject root = ProjectSerializer.parse(json);
        JSONArray timeline = root.getJSONArray("timeline");
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        long projectId = -1;
        try {
            projectId = ProjectMigration.insertProject(db, root.optString("name", "恢复项目") + " · 恢复");
            for (int i = 0; i < timeline.length(); i++) {
                ContentValues row = clipValues(ProjectSerializer.clipRow(timeline.getJSONObject(i), i));
                row.put("project_id", projectId);
                db.insertOrThrow("timeline_clip", null, row);
            }
            assets.importPlacements(db, projectId, root.optJSONArray("placements"));
            JSONArray subtitleCues = root.optJSONArray("subtitleCues");
            if (subtitleCues != null) {
                if (subtitleCues.length() > 10000) throw new IllegalArgumentException("项目归档字幕条目过多");
                for (int i = 0; i < subtitleCues.length(); i++) {
                    JSONObject value = subtitleCues.getJSONObject(i);
                    long start = value.optLong("startMs", -1), end = value.optLong("endMs", -1);
                    if (start < 0 || end <= start) continue;
                    ContentValues cue = new ContentValues();
                    cue.put("project_id", projectId);
                    cue.put("start_ms", start);
                    cue.put("end_ms", end);
                    cue.put("text", value.optString("text", ""));
                    cue.put("position", i);
                    db.insertOrThrow("project_subtitle_cue", null, cue);
                }
            }
            JSONObject tracks = root.optJSONObject("trackStates");
            if (tracks != null) {
                for (String type : new String[]{"ORIGINAL", "NARRATION", "MUSIC"}) {
                    JSONObject value = tracks.optJSONObject(type);
                    if (value == null) continue;
                    ContentValues state = new ContentValues();
                    state.put("project_id", projectId);
                    state.put("track_type", type);
                    state.put("muted", value.optBoolean("muted") ? 1 : 0);
                    state.put("solo", value.optBoolean("solo") ? 1 : 0);
                    db.insertWithOnConflict("project_track_state", null, state, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            JSONArray revisions = root.optJSONArray("revisions");
            if (revisions != null && revisions.length() <= 100) {
                for (int i = 0; i < revisions.length(); i++) {
                    JSONObject value = revisions.getJSONObject(i);
                    JSONArray values = value.optJSONArray("clips");
                    if (values == null || values.length() > 10000) continue;
                    ContentValues revision = new ContentValues();
                    revision.put("project_id", projectId);
                    revision.put("created_at", value.optLong("createdAt", System.currentTimeMillis()));
                    revision.put("reason", value.optString("reason", "恢复的版本"));
                    revision.put("clip_count", values.length());
                    long revisionId = db.insertOrThrow("project_revision", null, revision);
                    for (int j = 0; j < values.length(); j++) {
                        ContentValues row = clipValues(ProjectSerializer.clipRow(values.getJSONObject(j), j));
                        row.put("revision_id", revisionId);
                        db.insertOrThrow("revision_clip", null, row);
                    }
                }
            }
            ProjectMigration.setActiveProjectId(db, projectId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        activeProjectId = projectId;
        return projectId;
    }

    private static ContentValues clipValues(ProjectSerializer.ClipRow row) {
        ContentValues values = new ContentValues();
        values.put("position", row.position());
        values.put("clip_key", row.key());
        values.put("uri", row.uri());
        values.put("name", row.name());
        values.put("start_ms", row.startMs());
        values.put("end_ms", row.endMs());
        values.put("muted", row.muted() ? 1 : 0);
        values.put("subtitle", row.subtitle());
        values.put("narration", row.narration());
        values.put("effect_cue", row.effectCue());
        values.put("track", row.track());
        return values;
    }

    private void trimRevisions(SQLiteDatabase db) {
        db.execSQL("DELETE FROM project_revision WHERE project_id=" + activeProjectId
                + " AND id NOT IN (SELECT id FROM project_revision WHERE project_id=" + activeProjectId
                + " ORDER BY created_at DESC LIMIT 100)");
        db.execSQL("DELETE FROM revision_clip WHERE revision_id NOT IN (SELECT id FROM project_revision)");
    }

    private void recoverInterruptedProjects() {
        ContentValues interrupted = new ContentValues();
        interrupted.put("status", "FAILED");
        interrupted.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update("project", interrupted, "status='PROCESSING'", null);
    }
}
