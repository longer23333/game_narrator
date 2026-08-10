package cn.longer233.gamenarrator.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for export jobs, scoped to a project id passed by the caller.
 */
public final class MobileExportStore {
    public static final class ExportJobInfo {
        private final long id, createdAt, updatedAt;
        private final String status, outputPath, error, preset;
        private final int progress;
        ExportJobInfo(long id, String status, int progress, String outputPath, String error, String preset, long createdAt, long updatedAt) {
            this.id = id;
            this.status = status;
            this.progress = progress;
            this.outputPath = outputPath;
            this.error = error;
            this.preset = preset;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
        public long id() { return id; }
        public String status() { return status; }
        public int progress() { return progress; }
        public String outputPath() { return outputPath; }
        public String error() { return error; }
        public String preset() { return preset; }
        public long createdAt() { return createdAt; }
        public long updatedAt() { return updatedAt; }
    }

    private final MobileDatabase database;

    public MobileExportStore(MobileDatabase database) {
        this.database = database;
        recoverInterruptedExports();
    }

    public long startExport(long projectId, String outputPath, String preset) {
        long now = System.currentTimeMillis();
        ContentValues row = new ContentValues();
        row.put("project_id", projectId);
        row.put("status", "PROCESSING");
        row.put("progress", 0);
        row.put("output_path", outputPath);
        row.put("error", "");
        row.put("preset_label", preset == null ? "" : preset);
        row.put("created_at", now);
        row.put("updated_at", now);
        return database.getWritableDatabase().insertOrThrow("export_job", null, row);
    }

    public void updateExport(long projectId, long id, String status, int progress, String error) {
        ContentValues row = new ContentValues();
        row.put("status", status);
        row.put("progress", progress);
        row.put("error", error == null ? "" : error);
        row.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update("export_job", row, "id=? AND project_id=?",
                new String[]{String.valueOf(id), String.valueOf(projectId)});
    }

    public List<ExportJobInfo> listExports(long projectId) {
        List<ExportJobInfo> out = new ArrayList<>();
        try (Cursor c = database.getReadableDatabase().query("export_job",
                new String[]{"id", "status", "progress", "output_path", "error", "preset_label", "created_at", "updated_at"},
                "project_id=?", new String[]{String.valueOf(projectId)}, null, null, "created_at DESC", "50")) {
            while (c.moveToNext()) {
                out.add(new ExportJobInfo(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3), c.getString(4),
                        c.getString(5), c.getLong(6), c.getLong(7)));
            }
        }
        return out;
    }

    void deleteExportsForProject(SQLiteDatabase db, long projectId) {
        db.delete("export_job", "project_id=?", new String[]{String.valueOf(projectId)});
    }

    private void recoverInterruptedExports() {
        ContentValues interrupted = new ContentValues();
        interrupted.put("status", "FAILED");
        interrupted.put("error", ExportRecoveryPolicy.INTERRUPTED_ERROR);
        interrupted.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update("export_job", interrupted, "status='PROCESSING'", null);
    }
}
