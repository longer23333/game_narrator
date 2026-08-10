package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class SnapshotProjectStateTest {
    @Test public void snapshotUndoRedoRestoresSubtitleEffectAudioAndTrackData() {
        List<TimelineClip> clips = new ArrayList<>();
        TimelineClip clip = new TimelineClip(Uri.parse("content://media/one"), "one", 0, 1000);
        clips.add(clip);
        FakeProjectState state = new FakeProjectState(clips);
        TimelineController controller = new TimelineController(clips, state, noopListener());

        controller.pushSnapshot("字幕/效果/音频修改");
        clip.updateCreativeText("新字幕", "新解说", "[交叉转场]");
        state.subtitles.add(new SubtitleFileCodec.Cue(0, 500, "精确字幕"));
        state.tracks.add(new ProjectSnapshot.Track("NARRATION", true, false));
        state.keyframes.add(new ProjectSnapshot.Keyframe(clip.key(), "volume", 0, .5f, KeyframeEasing.LINEAR));
        state.audioConfigs.add(new ProjectSnapshot.AudioConfig(clip.key(), .4f, 100, 200));
        state.visualConfigs.add(new ProjectSnapshot.VisualConfig(clip.key(), .1f, 0, 0, 0, 0, 1, 0));
        state.voices.add(new ProjectSnapshot.Voice(clip.key(), "zh-CN", 1f, 1f));
        controller.captureSnapshot();

        assertTrue(controller.undo());
        assertEquals("", clips.get(0).subtitle());
        assertEquals("", clips.get(0).narration());
        assertEquals("", clips.get(0).effectCue());
        assertTrue(state.subtitles.isEmpty());
        assertTrue(state.tracks.isEmpty());
        assertTrue(state.keyframes.isEmpty());
        assertTrue(state.audioConfigs.isEmpty());
        assertTrue(state.visualConfigs.isEmpty());
        assertTrue(state.voices.isEmpty());

        assertTrue(controller.redo());
        assertEquals("新字幕", clips.get(0).subtitle());
        assertEquals("新解说", clips.get(0).narration());
        assertEquals("[交叉转场]", clips.get(0).effectCue());
        assertEquals(1, state.subtitles.size());
        assertEquals(1, state.tracks.size());
        assertEquals(1, state.keyframes.size());
        assertEquals(1, state.audioConfigs.size());
        assertEquals(1, state.visualConfigs.size());
        assertEquals(1, state.voices.size());
        assertEquals("精确字幕", state.subtitles.get(0).text());
        assertTrue(state.tracks.get(0).muted());
        assertEquals(.5f, state.keyframes.get(0).value(), .0001f);
        assertEquals(.4f, state.audioConfigs.get(0).volume(), .0001f);
        assertEquals(.1f, state.visualConfigs.get(0).brightness(), .0001f);
        assertEquals("zh-CN", state.voices.get(0).voiceName());
    }

    private static TimelineController.Listener noopListener() {
        return new TimelineController.Listener() {
            @Override public void onTimelineChanged() { }
            @Override public void onStatus(String message) { }
        };
    }
}
