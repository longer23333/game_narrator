package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Device stress scenarios for large media, process restarts and export
 * recovery. stress-4k.mp4 is a required test asset: a missing fixture must fail
 * the heavy-device gate instead of turning a 4K claim into a skipped test.
 */
@RunWith(AndroidJUnit4.class)
public final class DeviceStressTest {
    private static final String LARGE_ASSET = "stress-4k.mp4";

    @Test public void largeVideoOpensGeneratesThumbnailAndPlays() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = ensureAssetVideo(context, "stress", LARGE_ASSET);
        assertNotNull("stress-4k.mp4 test asset must be bundled", file);

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            assertNotNull("video width must be readable", width);
            assertNotNull("video height must be readable", height);
            assertTrue("stress fixture must be at least 3840 pixels wide", Integer.parseInt(width) >= 3840);
            assertTrue("stress fixture must be at least 2160 pixels high", Integer.parseInt(height) >= 2160);
            Bitmap frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            assertNotNull("thumbnail must be extractable", frame);
            assertTrue(frame.getWidth() > 0 && frame.getHeight() > 0);
        } finally {
            retriever.release();
        }

        Handler main = new Handler(Looper.getMainLooper());
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<ExoPlayer> playerRef = new AtomicReference<>();
        AtomicReference<Throwable> playerError = new AtomicReference<>();
        main.post(() -> {
            try {
                ExoPlayer player = new ExoPlayer.Builder(context).build();
                playerRef.set(player);
                player.addListener(new Player.Listener() {
                    @Override public void onPlaybackStateChanged(int state) {
                        if (state == ExoPlayer.STATE_READY) ready.countDown();
                    }
                });
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
                player.prepare();
                player.play();
            } catch (Throwable error) {
                playerError.set(error);
                ready.countDown();
            }
        });
        assertTrue("player did not reach READY", ready.await(60, TimeUnit.SECONDS));
        assertNull("player start failed: " + playerError.get(), playerError.get());
        Thread.sleep(1500);
        AtomicLong position = new AtomicLong(-1);
        CountDownLatch positionRead = new CountDownLatch(1);
        main.post(() -> {
            try {
                position.set(playerRef.get().getCurrentPosition());
            } finally {
                playerRef.get().release();
                positionRead.countDown();
            }
        });
        assertTrue("position read did not finish", positionRead.await(30, TimeUnit.SECONDS));
        assertTrue("playback must advance", position.get() > 0);
    }

    @Test public void exportProgressIsMarkedForRecoveryAfterRestart() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileProjectStore first = new MobileProjectStore(context);
        long jobId = first.startExport("content://stress/output.mp4", "高清 1080p");
        first.updateExport(jobId, "PROCESSING", 50, "");
        long projectId = first.activeProjectId();
        first.close();

        MobileProjectStore reopened = new MobileProjectStore(context);
        try {
            assertEquals(projectId, reopened.activeProjectId());
            boolean found = false;
            for (MobileExportStore.ExportJobInfo job : reopened.listExports()) {
                if (job.id() == jobId) {
                    found = true;
                    assertEquals("FAILED", job.status());
                    assertEquals(ExportRecoveryPolicy.INTERRUPTED_ERROR, job.error());
                }
            }
            assertTrue("export job must survive reopen", found);
        } finally {
            reopened.close();
        }
    }

    @Test public void backupManagerWritesArchiveToPrivateStorage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileProjectStore store = new MobileProjectStore(context);
        ProjectBackupManager backups = new ProjectBackupManager(context, store.projects());
        try {
            File backup = backups.backupNow();
            assertTrue(backup.isFile());
            assertTrue(backup.length() > 0);
        } finally {
            backups.close();
            store.close();
        }
    }

    @Test public void persistentHistorySurvivesStoreReopen() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileProjectStore first = new MobileProjectStore(context);
        long projectId = first.activeProjectId();
        List<TimelineClip> clips = new ArrayList<>();
        clips.add(new TimelineClip("k1", Uri.parse("content://media/1"), "片段", 0, 1000));
        ProjectSnapshot snapshot = ProjectSnapshot.capture(first, clips);
        new PersistentSnapshotHistory(new SqliteEditHistoryStore(first.database())).push(projectId, snapshot);
        first.close();

        MobileProjectStore reopened = new MobileProjectStore(context);
        try {
            ProjectSnapshot restored = new PersistentSnapshotHistory(
                    new SqliteEditHistoryStore(reopened.database())).latest(projectId);
            assertNotNull(restored);
            assertEquals(1, restored.clips().size());
            assertEquals("k1", restored.clips().get(0).key());
        } finally {
            reopened.close();
        }
    }

    @Test public void taskBriefPersistsAcrossStoreReopen() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileProjectStore first = new MobileProjectStore(context);
        long projectId = first.createProject("魂系 Boss 战");
        first.updateTaskBrief(new ProjectRepository.TaskBrief("STORY", "PASSIONATE", "HIGHLIGHTS", 90,
                "保留高光", "大龙=纳什男爵", false, true));
        first.selectProject(projectId);
        first.close();

        MobileProjectStore reopened = new MobileProjectStore(context);
        try {
            reopened.selectProject(projectId);
            ProjectRepository.TaskBrief brief = reopened.taskBrief();
            assertEquals("STORY", brief.gameCategory());
            assertEquals("PASSIONATE", brief.commentaryStyle());
            assertEquals("HIGHLIGHTS", brief.editingScope());
            assertEquals(90, brief.targetDurationSeconds());
            assertEquals("保留高光", brief.brief());
            assertEquals("大龙=纳什男爵", brief.terminologyGlossary());
        } finally {
            reopened.close();
        }
    }

    @Test public void modelDirectoryDefaultsToNotInstalled() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File empty = new File(context.getCacheDir(), "empty-models");
        if (!empty.isDirectory()) empty.mkdirs();
        MobileModelDirectory.Presence presence = MobileModelDirectory.check(empty);
        assertFalse(presence.whisper());
        assertFalse(presence.vision());
        assertFalse(presence.text());
    }

    private static File ensureAssetVideo(Context target, String dir, String name) throws Exception {
        File folder = new File(target.getExternalFilesDir(null), dir);
        if (!folder.isDirectory() && !folder.mkdirs()) return null;
        File file = new File(folder, name);
        if (file.isFile() && file.length() > 0) return file;
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        InputStream in = null;
        try {
            in = testContext.getAssets().open(name);
        } catch (IOException missingInTestApk) {
            in = target.getAssets().open(name);
        }
        try (InputStream source = in;
             OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) >= 0) out.write(buffer, 0, read);
        } catch (IOException missing) {
            return null;
        }
        return file;
    }
}
