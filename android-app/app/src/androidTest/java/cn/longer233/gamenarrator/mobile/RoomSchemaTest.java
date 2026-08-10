package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RoomSchemaTest {
    @Test public void allEntitiesRoundTripAndEditHistoryTrims() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GameNarratorRoomDatabase db = GameNarratorRoomDatabase.inMemory(context);
        try {
            ProjectEntity project = new ProjectEntity();
            project.name = "测试项目";
            project.status = "EDITING";
            project.pipelineStage = "RENDER";
            project.createdAt = 1;
            project.updatedAt = 2;
            long projectId = db.projectDao().insert(project);

            TimelineClipEntity clip = new TimelineClipEntity();
            clip.projectId = projectId;
            clip.position = 0;
            clip.clipKey = "k1";
            clip.uri = "content://media/1";
            clip.name = "片段";
            clip.startMs = 0;
            clip.endMs = 1000;
            clip.muted = 0;
            clip.subtitle = "字幕";
            clip.narration = "解说";
            clip.effectCue = "特效";
            clip.track = "V1";
            db.timelineClipDao().insert(clip);

            AssetEntity asset = new AssetEntity();
            asset.uri = "content://media/1";
            asset.name = "素材";
            asset.mediaType = "video/mp4";
            asset.tags = "";
            asset.createdAt = 1;
            db.assetDao().insert(asset);

            ExportJobEntity job = new ExportJobEntity();
            job.projectId = projectId;
            job.status = "PROCESSING";
            job.progress = 50;
            job.outputPath = "/tmp/out.mp4";
            job.error = "";
            job.presetLabel = "高清 1080p";
            job.createdAt = 1;
            job.updatedAt = 2;
            db.exportJobDao().insert(job);

            assertEquals(1, db.projectDao().all().size());
            assertEquals("RENDER", db.projectDao().all().get(0).pipelineStage);
            assertEquals("k1", db.timelineClipDao().all().get(0).clipKey);
            assertEquals("video/mp4", db.assetDao().all().get(0).mediaType);
            assertEquals(50, db.exportJobDao().all().get(0).progress);

            RoomEditHistoryStore store = new RoomEditHistoryStore(db.editHistoryDao());
            for (int i = 1; i <= 60; i++) store.push(projectId, "snapshot-" + i);
            assertEquals(50, store.count(projectId));
            assertEquals("snapshot-60", store.recent(projectId, 1).get(0));
            List<String> tail = store.recent(projectId, 50);
            assertTrue(tail.contains("snapshot-11"));
            store.clear(projectId);
            assertEquals(0, store.count(projectId));
        } finally {
            db.close();
        }
    }
}
