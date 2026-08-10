package cn.longer233.gamenarrator.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Schema DDL, initial seeding and incremental version upgrades for the mobile
 * database. {@link MobileDatabase} owns the SQLiteOpenHelper lifecycle and
 * delegates creation and upgrades here.
 */
public final class ProjectMigration {
    private ProjectMigration() { }

    static void createAll(SQLiteDatabase db) {
        createProjectTable(db);
        db.execSQL("CREATE TABLE timeline_clip (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, position INTEGER NOT NULL, clip_key TEXT NOT NULL, uri TEXT NOT NULL, name TEXT NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, muted INTEGER NOT NULL, subtitle TEXT NOT NULL, narration TEXT NOT NULL, effect_cue TEXT NOT NULL, track TEXT NOT NULL DEFAULT 'V1')");
        createRevisionTables(db);
        createMetaTable(db);
        createExportTable(db);
        createAssetTables(db);
        createCompilationTables(db);
        createTrackStateTable(db);
        createSubtitleCueTable(db);
        createClipReviewTable(db);
        createVoiceConfigTable(db);
        createClipAudioTable(db);
        createClipVisualTable(db);
        createKeyframeTable(db);
        createEffectTemplateTable(db);
        createEditHistoryTable(db);
        long id = insertProject(db, "未命名项目");
        setActiveProjectId(db, id);
    }

    static void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createRevisionTables(db);
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE project RENAME TO project_legacy");
            createProjectTable(db);
            db.execSQL("INSERT INTO project(id,name,status,created_at,updated_at) SELECT id,name,'EDITING',updated_at,updated_at FROM project_legacy");
            db.execSQL("DROP TABLE project_legacy");
            createMetaTable(db);
            setActiveProjectId(db, 1);
        }
        if (oldVersion < 4) createExportTable(db);
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE timeline_clip ADD COLUMN narration TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE timeline_clip ADD COLUMN effect_cue TEXT NOT NULL DEFAULT ''");
            if (oldVersion >= 2) {
                db.execSQL("ALTER TABLE revision_clip ADD COLUMN narration TEXT NOT NULL DEFAULT ''");
                db.execSQL("ALTER TABLE revision_clip ADD COLUMN effect_cue TEXT NOT NULL DEFAULT ''");
            }
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE timeline_clip ADD COLUMN clip_key TEXT NOT NULL DEFAULT ''");
            if (oldVersion >= 2) db.execSQL("ALTER TABLE revision_clip ADD COLUMN clip_key TEXT NOT NULL DEFAULT ''");
            createAssetTables(db);
        }
        if (oldVersion < 7 && oldVersion >= 6) {
            db.execSQL("ALTER TABLE asset_placement ADD COLUMN volume REAL NOT NULL DEFAULT 1.0");
            db.execSQL("ALTER TABLE asset_placement ADD COLUMN fade_in_ms INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE asset_placement ADD COLUMN fade_out_ms INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 8) createCompilationTables(db);
        if (oldVersion < 9 && oldVersion >= 6) {
            db.execSQL("ALTER TABLE asset_placement ADD COLUMN visual_x REAL NOT NULL DEFAULT 0.68");
            db.execSQL("ALTER TABLE asset_placement ADD COLUMN visual_y REAL NOT NULL DEFAULT 0.66");
            db.execSQL("ALTER TABLE asset_placement ADD COLUMN visual_scale REAL NOT NULL DEFAULT 0.31");
        }
        if (oldVersion < 10 && oldVersion >= 4) db.execSQL("ALTER TABLE export_job ADD COLUMN preset_label TEXT NOT NULL DEFAULT ''");
        if (oldVersion < 11) createTrackStateTable(db);
        if (oldVersion < 12 && oldVersion >= 3) db.execSQL("ALTER TABLE project ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 13) createSubtitleCueTable(db);
        if (oldVersion < 14) createClipReviewTable(db);
        if (oldVersion < 15) createVoiceConfigTable(db);
        if (oldVersion < 16) createClipAudioTable(db);
        if (oldVersion < 17) createClipVisualTable(db);
        if (oldVersion < 18 && oldVersion >= 17) {
            db.execSQL("ALTER TABLE project_clip_visual ADD COLUMN brightness REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE project_clip_visual ADD COLUMN temperature REAL NOT NULL DEFAULT 0");
        }
        if (oldVersion < 19) createKeyframeTable(db);
        if (oldVersion < 20 && oldVersion >= 19) db.execSQL("ALTER TABLE project_clip_keyframe ADD COLUMN easing TEXT NOT NULL DEFAULT 'LINEAR'");
        if (oldVersion < 21) createEffectTemplateTable(db);
        if (oldVersion < 22) db.execSQL("ALTER TABLE project ADD COLUMN pipeline_stage TEXT NOT NULL DEFAULT 'IMPORT'");
        if (oldVersion < 23) {
            db.execSQL("ALTER TABLE timeline_clip ADD COLUMN track TEXT NOT NULL DEFAULT 'V1'");
            if (oldVersion >= 2) db.execSQL("ALTER TABLE revision_clip ADD COLUMN track TEXT NOT NULL DEFAULT 'V1'");
        }
        if (oldVersion < 24) createEditHistoryTable(db);
        if (oldVersion < 25) {
            db.execSQL("ALTER TABLE project ADD COLUMN brief TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE project ADD COLUMN game_category TEXT NOT NULL DEFAULT 'ACTION'");
            db.execSQL("ALTER TABLE project ADD COLUMN commentary_style TEXT NOT NULL DEFAULT 'ANIME_THEATER'");
            db.execSQL("ALTER TABLE project ADD COLUMN editing_scope TEXT NOT NULL DEFAULT 'FULL_VIDEO'");
            db.execSQL("ALTER TABLE project ADD COLUMN target_duration_seconds INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE project ADD COLUMN terminology_glossary TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE project ADD COLUMN storyboard_review_enabled INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE project ADD COLUMN automatic_generation_enabled INTEGER NOT NULL DEFAULT 1");
        }
    }

    private static void createProjectTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE project (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, status TEXT NOT NULL, pipeline_stage TEXT NOT NULL DEFAULT 'IMPORT', archived INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, brief TEXT NOT NULL DEFAULT '', game_category TEXT NOT NULL DEFAULT 'ACTION', commentary_style TEXT NOT NULL DEFAULT 'ANIME_THEATER', editing_scope TEXT NOT NULL DEFAULT 'FULL_VIDEO', target_duration_seconds INTEGER NOT NULL DEFAULT 0, terminology_glossary TEXT NOT NULL DEFAULT '', storyboard_review_enabled INTEGER NOT NULL DEFAULT 1, automatic_generation_enabled INTEGER NOT NULL DEFAULT 1)");
    }

    private static void createMetaTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS app_meta (key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }

    private static void createRevisionTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_revision (id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL,created_at INTEGER NOT NULL,reason TEXT NOT NULL,clip_count INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS revision_clip (id INTEGER PRIMARY KEY AUTOINCREMENT,revision_id INTEGER NOT NULL,position INTEGER NOT NULL,clip_key TEXT NOT NULL,uri TEXT NOT NULL,name TEXT NOT NULL,start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL,muted INTEGER NOT NULL,subtitle TEXT NOT NULL,narration TEXT NOT NULL,effect_cue TEXT NOT NULL,track TEXT NOT NULL DEFAULT 'V1')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_revision_project ON project_revision(project_id,created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_revision_clip ON revision_clip(revision_id,position)");
    }

    private static void createExportTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS export_job (id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL,status TEXT NOT NULL,progress INTEGER NOT NULL,output_path TEXT NOT NULL,error TEXT NOT NULL,preset_label TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_export_project ON export_job(project_id,created_at DESC)");
    }

    private static void createAssetTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS asset (id INTEGER PRIMARY KEY AUTOINCREMENT,uri TEXT NOT NULL UNIQUE,name TEXT NOT NULL,media_type TEXT NOT NULL,tags TEXT NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS asset_placement (id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL,clip_key TEXT NOT NULL,asset_id INTEGER NOT NULL,role TEXT NOT NULL,volume REAL NOT NULL DEFAULT 1.0,fade_in_ms INTEGER NOT NULL DEFAULT 0,fade_out_ms INTEGER NOT NULL DEFAULT 0,visual_x REAL NOT NULL DEFAULT 0.68,visual_y REAL NOT NULL DEFAULT 0.66,visual_scale REAL NOT NULL DEFAULT 0.31,created_at INTEGER NOT NULL,UNIQUE(project_id,clip_key,asset_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_asset_placement_project ON asset_placement(project_id,clip_key)");
    }

    private static void createCompilationTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS clip_compilation (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS clip_compilation_item (id INTEGER PRIMARY KEY AUTOINCREMENT,compilation_id INTEGER NOT NULL,project_id INTEGER NOT NULL,clip_key TEXT NOT NULL,position INTEGER NOT NULL,created_at INTEGER NOT NULL,UNIQUE(compilation_id,project_id,clip_key),UNIQUE(compilation_id,position))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_compilation_item_order ON clip_compilation_item(compilation_id,position)");
    }

    private static void createTrackStateTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_track_state(project_id INTEGER NOT NULL,track_type TEXT NOT NULL,muted INTEGER NOT NULL DEFAULT 0,solo INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(project_id,track_type))");
    }

    private static void createSubtitleCueTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_subtitle_cue(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL,start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL,text TEXT NOT NULL,position INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_project_subtitle_time ON project_subtitle_cue(project_id,start_ms,end_ms)");
    }

    private static void createClipReviewTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_clip_review(project_id INTEGER NOT NULL,clip_key TEXT NOT NULL,status TEXT NOT NULL,note TEXT NOT NULL,updated_at INTEGER NOT NULL,PRIMARY KEY(project_id,clip_key))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clip_review_project ON project_clip_review(project_id,status)");
    }

    private static void createVoiceConfigTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_clip_voice(project_id INTEGER NOT NULL,clip_key TEXT NOT NULL,voice_name TEXT NOT NULL,speed REAL NOT NULL DEFAULT 1.0,pitch REAL NOT NULL DEFAULT 1.0,PRIMARY KEY(project_id,clip_key))");
    }

    private static void createClipAudioTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_clip_audio(project_id INTEGER NOT NULL,clip_key TEXT NOT NULL,volume REAL NOT NULL DEFAULT 1.0,fade_in_ms INTEGER NOT NULL DEFAULT 0,fade_out_ms INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(project_id,clip_key))");
    }

    private static void createClipVisualTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_clip_visual(project_id INTEGER NOT NULL,clip_key TEXT NOT NULL,contrast REAL NOT NULL DEFAULT 0,saturation REAL NOT NULL DEFAULT 0,hue REAL NOT NULL DEFAULT 0,scale REAL NOT NULL DEFAULT 1,rotation REAL NOT NULL DEFAULT 0,brightness REAL NOT NULL DEFAULT 0,temperature REAL NOT NULL DEFAULT 0,PRIMARY KEY(project_id,clip_key))");
    }

    private static void createKeyframeTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_clip_keyframe(project_id INTEGER NOT NULL,clip_key TEXT NOT NULL,property TEXT NOT NULL,time_ms INTEGER NOT NULL,value REAL NOT NULL,easing TEXT NOT NULL DEFAULT 'LINEAR',PRIMARY KEY(project_id,clip_key,property,time_ms))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clip_keyframe ON project_clip_keyframe(project_id,clip_key,property,time_ms)");
    }

    private static void createEffectTemplateTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS effect_template(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,cue TEXT NOT NULL,created_at INTEGER NOT NULL)");
    }

    private static void createEditHistoryTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS edit_history (id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL,payload TEXT NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edit_history_project ON edit_history(project_id,id DESC)");
    }

    static long insertProject(SQLiteDatabase db, String name) {
        long now = System.currentTimeMillis();
        ContentValues row = new ContentValues();
        row.put("name", name);
        row.put("status", "DRAFT");
        row.put("created_at", now);
        row.put("updated_at", now);
        return db.insertOrThrow("project", null, row);
    }

    static void setActiveProjectId(SQLiteDatabase db, long id) {
        ContentValues row = new ContentValues();
        row.put("key", "active_project_id");
        row.put("value", String.valueOf(id));
        db.insertWithOnConflict("app_meta", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    static long readActiveProjectId(SQLiteDatabase db) {
        try (Cursor c = db.query("app_meta", new String[]{"value"}, "key='active_project_id'", null, null, null, null)) {
            if (c.moveToFirst()) return Long.parseLong(c.getString(0));
        }
        try (Cursor c = db.query("project", new String[]{"id"}, null, null, null, null, "updated_at DESC", "1")) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        long id = insertProject(db, "未命名项目");
        setActiveProjectId(db, id);
        return id;
    }
}
