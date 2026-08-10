package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class ProjectControllerTest {
    @Test public void editFlowPersistsAndUndoRedoRestores() {
        List<TimelineClip> clips = new ArrayList<>();
        clips.add(clip("one"));
        FakeProjectState state = new FakeProjectState(clips);
        TimelineController timeline = new TimelineController(clips, state, noopListener());
        FakeProjectStore store = new FakeProjectStore();
        ProjectController projects = new ProjectController(store, clips, timeline);

        projects.beginEdit("字幕修改");
        clips.get(0).updateCreativeText("新字幕", "新解说", "[交叉转场]");
        projects.commitEdit("字幕修改");

        assertEquals("新字幕", store.lastSavedClips.get(0).subtitle());
        assertTrue(projects.canUndo());
        assertTrue(projects.undo());
        assertEquals("", clips.get(0).subtitle());
        assertTrue(projects.canRedo());
        assertTrue(projects.redo());
        assertEquals("新字幕", clips.get(0).subtitle());
        assertTrue(projects.canUndo());
    }

    @Test public void projectControllerSnapshotCoversSubtitleAudioAndEffectEdits() {
        List<TimelineClip> clips = new ArrayList<>();
        TimelineClip clip = new TimelineClip("one", Uri.parse("content://media/one"), "one", 0, 1000);
        clips.add(clip);
        FakeProjectState state = new FakeProjectState(clips);
        state.subtitles.add(new SubtitleFileCodec.Cue(0, 500, "旧字幕"));
        state.audioConfigs.add(new ProjectSnapshot.AudioConfig("one", 0.5f, 0, 100));
        state.visualConfigs.add(new ProjectSnapshot.VisualConfig("one", 1f, 1f, 1f, 0f, 0f, 1f, 0f));
        state.tracks.add(new ProjectSnapshot.Track("ORIGINAL", false, false));
        TimelineController timeline = new TimelineController(clips, state, noopListener());
        FakeProjectStore store = new FakeProjectStore();
        ProjectController projects = new ProjectController(store, clips, timeline);

        projects.beginEdit("字幕/音频/特效修改");
        state.subtitles.set(0, new SubtitleFileCodec.Cue(200, 800, "新字幕"));
        state.audioConfigs.set(0, new ProjectSnapshot.AudioConfig("one", 1f, 50, 200));
        state.visualConfigs.set(0, new ProjectSnapshot.VisualConfig("one", 1.2f, 1.1f, 0.9f, 0f, 0f, 0.8f, 15f));
        state.tracks.set(0, new ProjectSnapshot.Track("ORIGINAL", true, false));
        state.keyframes.add(new ProjectSnapshot.Keyframe("one", "volume", 300, 0.8f, KeyframeEasing.LINEAR));
        projects.commitEdit("字幕/音频/特效修改");

        assertEquals(200, state.subtitles.get(0).startMs());
        assertEquals(1f, state.audioConfigs.get(0).volume(), 0.0001f);
        assertTrue(state.tracks.get(0).muted());
        assertEquals(1.2f, state.visualConfigs.get(0).brightness(), 0.0001f);
        assertEquals(1, state.keyframes.size());

        assertTrue(projects.undo());
        assertEquals(0, state.subtitles.get(0).startMs());
        assertEquals(0.5f, state.audioConfigs.get(0).volume(), 0.0001f);
        assertFalse(state.tracks.get(0).muted());
        assertEquals(1f, state.visualConfigs.get(0).brightness(), 0.0001f);
        assertTrue(state.keyframes.isEmpty());

        assertTrue(projects.redo());
        assertEquals(200, state.subtitles.get(0).startMs());
        assertEquals(1f, state.audioConfigs.get(0).volume(), 0.0001f);
        assertTrue(state.tracks.get(0).muted());
        assertEquals(1.2f, state.visualConfigs.get(0).brightness(), 0.0001f);
        assertEquals(1, state.keyframes.size());
    }

    @Test public void recoveryDeletesOnlyAppOwnedInterruptedOutputs() throws Exception {
        File temp = File.createTempFile("recovery", ".dir");
        assertTrue(temp.delete());
        assertTrue(temp.mkdirs());
        File movies = new File(temp, "movies");
        assertTrue(movies.mkdirs());
        try {
            File partial = new File(movies, "partial.mp4");
            assertTrue(partial.createNewFile());
            File outside = new File(temp, "outside.mp4");
            assertTrue(outside.createNewFile());
            FakeProjectStore store = new FakeProjectStore();
            store.jobs.add(job(1, "FAILED", partial.getAbsolutePath(), ExportRecoveryPolicy.INTERRUPTED_ERROR));
            store.jobs.add(job(2, "FAILED", outside.getAbsolutePath(), ExportRecoveryPolicy.INTERRUPTED_ERROR));
            store.jobs.add(job(3, "FAILED", partial.getAbsolutePath(), "disk full"));
            store.jobs.add(job(4, "COMPLETED", partial.getAbsolutePath(), ""));
            ProjectController projects = new ProjectController(store, new ArrayList<>(), new TimelineController(
                    new ArrayList<>(), new FakeProjectState(new ArrayList<>()), noopListener()));

            assertEquals(1, projects.recoverInterruptedExports(movies));
            assertFalse(partial.exists());
            assertTrue(outside.exists());
        } finally {
            deleteRecursively(temp);
        }
    }

    @Test public void restoreProjectArchiveReloadsClipsAndClearsHistory() throws Exception {
        List<TimelineClip> clips = new ArrayList<>();
        FakeProjectState state = new FakeProjectState(clips);
        TimelineController timeline = new TimelineController(clips, state, noopListener());
        FakeProjectStore store = new FakeProjectStore();
        ProjectController projects = new ProjectController(store, clips, timeline);
        projects.beginEdit("编辑");
        clips.add(clip("one"));
        projects.commitEdit("编辑");
        assertTrue(projects.canUndo());

        store.storedClips.add(clip("restored"));
        projects.restoreProjectArchive("{\"format\":\"GameNarratorAndroidProject\"}");
        assertTrue(store.imported);
        assertEquals(1, clips.size());
        assertEquals("restored", clips.get(0).name());
        assertFalse(projects.canUndo());
        assertEquals("{\"format\":\"GameNarratorAndroidProject\"}", projects.buildProjectArchive());
    }

    private static MobileExportStore.ExportJobInfo job(long id, String status, String path, String error) {
        return new MobileExportStore.ExportJobInfo(id, status, 0, path, error, "preset", 0, 0);
    }

    private static TimelineClip clip(String name) {
        return new TimelineClip(Uri.parse("content://media/" + name), name, 0, 1000);
    }

    private static TimelineController.Listener noopListener() {
        return new TimelineController.Listener() {
            @Override public void onTimelineChanged() { }
            @Override public void onStatus(String message) { }
        };
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
