package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Owns the SQLiteOpenHelper lifecycle; schema creation and version upgrades are
 * delegated to {@link ProjectMigration}.
 */
public final class MobileDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "game-narrator-mobile.db";
    private static final int DB_VERSION = 26;

    public MobileDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        ProjectMigration.createAll(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ProjectMigration.upgrade(db, oldVersion, newVersion);
    }
}
