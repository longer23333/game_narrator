package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RoomMigrationTest {
    @Test public void copiesLegacySchemaIntoRoomPreservingIds() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File legacyFile = new File(context.getFilesDir(), "legacy-migration.db");
        File roomFile = context.getDatabasePath("room-migration-test.db");
        legacyFile.delete();
        roomFile.delete();

        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(legacyFile, null);
        ProjectMigration.createAll(legacy);
        ContentValues project = new ContentValues();
        project.put("name", "旧项目");
        project.put("status", "EDITING");
        project.put("created_at", 1L);
        project.put("updated_at", 2L);
        long projectId = legacy.insertOrThrow("project", null, project);
        ContentValues clip = new ContentValues();
        clip.put("project_id", projectId);
        clip.put("position", 0);
        clip.put("clip_key", "k1");
        clip.put("uri", "content://media/1");
        clip.put("name", "片段");
        clip.put("start_ms", 0L);
        clip.put("end_ms", 1000L);
        clip.put("muted", 0);
        clip.put("subtitle", "字幕");
        clip.put("narration", "解说");
        clip.put("effect_cue", "特效");
        clip.put("track", "V1");
        legacy.insertOrThrow("timeline_clip", null, clip);
        ContentValues asset = new ContentValues();
        asset.put("uri", "content://media/1");
        asset.put("name", "素材");
        asset.put("media_type", "video/mp4");
        asset.put("tags", "");
        asset.put("created_at", 1L);
        legacy.insertOrThrow("asset", null, asset);
        ContentValues job = new ContentValues();
        job.put("project_id", projectId);
        job.put("status", "COMPLETED");
        job.put("progress", 100);
        job.put("output_path", "/tmp/out.mp4");
        job.put("error", "");
        job.put("preset_label", "高清 1080p");
        job.put("created_at", 1L);
        job.put("updated_at", 2L);
        legacy.insertOrThrow("export_job", null, job);
        ContentValues history = new ContentValues();
        history.put("project_id", projectId);
        history.put("payload", "snapshot-json");
        history.put("created_at", 1L);
        legacy.insertOrThrow("edit_history", null, history);
        legacy.close();

        GameNarratorRoomDatabase room = Room.databaseBuilder(context, GameNarratorRoomDatabase.class,
                "room-migration-test.db").allowMainThreadQueries().build();
        try {
            RoomMigration.migrate(room, legacyFile.getAbsolutePath());
            boolean projectMigrated = false;
            for (ProjectEntity value : room.projectDao().all()) {
                if (value.id == projectId && "旧项目".equals(value.name)) projectMigrated = true;
            }
            assertTrue("legacy project must be migrated with its id", projectMigrated);
            assertEquals("k1", room.timelineClipDao().all().get(0).clipKey);
            assertEquals("video/mp4", room.assetDao().all().get(0).mediaType);
            assertEquals(100, room.exportJobDao().all().get(0).progress);
            assertEquals(1, room.editHistoryDao().count(projectId));
        } finally {
            room.close();
            legacyFile.delete();
            roomFile.delete();
        }
    }
}
