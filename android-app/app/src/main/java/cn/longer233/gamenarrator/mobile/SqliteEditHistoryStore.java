package cn.longer233.gamenarrator.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed edit history; keeps at most 50 payloads per project.
 */
public final class SqliteEditHistoryStore implements EditHistoryStore {
    public static final int MAX_ENTRIES = 50;

    private final MobileDatabase database;

    public SqliteEditHistoryStore(MobileDatabase database) {
        this.database = database;
    }

    @Override public void push(long projectId, String payload) {
        SQLiteDatabase db = database.getWritableDatabase();
        ContentValues row = new ContentValues();
        row.put("project_id", projectId);
        row.put("payload", payload);
        row.put("created_at", System.currentTimeMillis());
        db.insertOrThrow("edit_history", null, row);
        trim(db, projectId);
    }

    @Override public List<String> recent(long projectId, int limit) {
        List<String> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query("edit_history", new String[]{"payload"},
                "project_id=?", new String[]{String.valueOf(projectId)}, null, null, "id DESC",
                String.valueOf(Math.max(1, limit)))) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    @Override public void clear(long projectId) {
        database.getWritableDatabase().delete("edit_history", "project_id=?",
                new String[]{String.valueOf(projectId)});
    }

    @Override public int count(long projectId) {
        try (Cursor c = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM edit_history WHERE project_id=?",
                new String[]{String.valueOf(projectId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private void trim(SQLiteDatabase db, long projectId) {
        db.execSQL("DELETE FROM edit_history WHERE project_id=? AND id NOT IN "
                        + "(SELECT id FROM edit_history WHERE project_id=? ORDER BY id DESC LIMIT " + MAX_ENTRIES + ")",
                new Object[]{projectId, projectId});
    }
}
