package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the single production SQLite database across a real close/reopen. */
@RunWith(AndroidJUnit4.class)
public final class ProductionDatabaseTest {
    @Test public void projectTimelineAndHistorySurviveReopen() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("game-narrator-mobile.db");

        long projectId;
        MobileProjectStore first = new MobileProjectStore(context);
        try {
            projectId = first.createProject("持久化项目");
            first.selectProject(projectId);
            TimelineClip clip = new TimelineClip("clip-1", Uri.parse("content://media/1"), "片段", 0, 1000);
            clip.update(0, 1000, false, "字幕");
            clip.updateCreativeText("字幕", "解说", "");
            first.saveClips(List.of(clip), "首次保存");
            first.saveClipVisualConfig("clip-1", .1f, .2f, 10, 5, 0, 1, 0, "/data/user/0/test/luts/demo.cube");
            new SqliteEditHistoryStore(first.database()).push(projectId, "snapshot-1");
        } finally {
            first.close();
        }

        MobileProjectStore reopened = new MobileProjectStore(context);
        try {
            reopened.selectProject(projectId);
            assertEquals("持久化项目", reopened.activeProjectName());
            assertEquals(1, reopened.loadClips().size());
            assertEquals("clip-1", reopened.loadClips().get(0).key());
            assertEquals("/data/user/0/test/luts/demo.cube", reopened.clipVisualConfig("clip-1").lutPath());
            List<String> history = new SqliteEditHistoryStore(reopened.database()).recent(projectId, 1);
            assertFalse(history.isEmpty());
            assertEquals("snapshot-1", history.get(0));
            assertTrue(reopened.database().getReadableDatabase().getVersion() >= 1);
        } finally {
            reopened.close();
            context.deleteDatabase("game-narrator-mobile.db");
        }
    }

    @Test public void publicAssetLicenseMetadataSurvivesReopen() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("game-narrator-mobile.db");
        PublicAsset source = new PublicAsset("PEXELS", "Victory", "Ada", "Pexels License",
                "https://www.pexels.com/license/", "https://www.pexels.com/photo/1", "",
                "https://images.pexels.com/photos/1.jpg", "image");
        MobileProjectStore first = new MobileProjectStore(context);
        try { assertTrue(first.addAsset(Uri.fromFile(new File(context.getFilesDir(), "victory.jpg")).toString(), "Victory", "image/jpeg", source) > 0); }
        finally { first.close(); }
        MobileProjectStore reopened = new MobileProjectStore(context);
        try {
            MobileAssetStore.AssetInfo asset = reopened.listAssets().get(0);
            assertEquals("PEXELS", asset.sourceProvider());
            assertEquals("Pexels License", asset.licenseName());
            assertEquals("Ada", asset.creator());
            assertEquals("https://www.pexels.com/photo/1", asset.sourceUrl());
        } finally { reopened.close(); context.deleteDatabase("game-narrator-mobile.db"); }
    }
}
