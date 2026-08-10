package cn.longer233.gamenarrator.mobile;

import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Copies the legacy SQLiteOpenHelper schema into the Room database with the
 * same table names, preserving ids so foreign-key relations stay intact.
 */
public final class RoomMigration {
    private RoomMigration() { }

    public static void migrate(GameNarratorRoomDatabase room, String legacyDatabasePath) {
        SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
        String attached = legacyDatabasePath.replace("'", "''");
        db.execSQL("ATTACH DATABASE '" + attached + "' AS legacy");
        try {
            db.execSQL("INSERT OR IGNORE INTO project(id,name,status,pipeline_stage,archived,created_at,updated_at) "
                    + "SELECT id,name,status,pipeline_stage,archived,created_at,updated_at FROM legacy.project");
            db.execSQL("INSERT OR IGNORE INTO timeline_clip(id,project_id,position,clip_key,uri,name,start_ms,end_ms,muted,subtitle,narration,effect_cue,track) "
                    + "SELECT id,project_id,position,clip_key,uri,name,start_ms,end_ms,muted,subtitle,narration,effect_cue,track FROM legacy.timeline_clip");
            db.execSQL("INSERT OR IGNORE INTO asset(id,uri,name,media_type,tags,created_at) "
                    + "SELECT id,uri,name,media_type,tags,created_at FROM legacy.asset");
            db.execSQL("INSERT OR IGNORE INTO export_job(id,project_id,status,progress,output_path,error,preset_label,created_at,updated_at) "
                    + "SELECT id,project_id,status,progress,output_path,error,preset_label,created_at,updated_at FROM legacy.export_job");
            db.execSQL("INSERT OR IGNORE INTO edit_history(id,project_id,payload,created_at) "
                    + "SELECT id,project_id,payload,created_at FROM legacy.edit_history");
        } finally {
            db.execSQL("DETACH DATABASE legacy");
        }
    }
}
