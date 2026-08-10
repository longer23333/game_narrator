package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class TimelineCommandsTest {
    private static TimelineClip clip(String name, long startMs, long endMs) {
        TimelineClip value = new TimelineClip(Uri.parse("content://media/" + name), name, startMs, endMs);
        value.update(startMs, endMs, false, name + " subtitle");
        return value;
    }

    @Test public void splitUndoRedo() {
        List<TimelineClip> clips = new ArrayList<>();
        clips.add(clip("one", 0, 1000));
        clips.add(clip("two", 0, 500));
        FakeProjectState state = new FakeProjectState(clips);
        TimelineController controller = new TimelineController(clips, state, noopListener());

        assertTrue(controller.split(0, 400));
        assertEquals(3, clips.size());
        assertEquals(0, clips.get(0).startMs());
        assertEquals(400, clips.get(0).endMs());
        assertEquals(400, clips.get(1).startMs());
        assertEquals(1000, clips.get(1).endMs());

        assertTrue(controller.undo());
        assertEquals(2, clips.size());
        assertEquals(1000, clips.get(0).endMs());

        assertTrue(controller.redo());
        assertEquals(3, clips.size());
        assertEquals(400, clips.get(1).startMs());
    }

    @Test public void deleteUndoRedo() {
        List<TimelineClip> clips = new ArrayList<>();
        clips.add(clip("one", 0, 1000));
        clips.add(clip("two", 0, 500));
        FakeProjectState state = new FakeProjectState(clips);
        TimelineController controller = new TimelineController(clips, state, noopListener());

        assertTrue(controller.remove(0));
        assertEquals(1, clips.size());
        assertEquals("two", clips.get(0).name());

        assertTrue(controller.undo());
        assertEquals(2, clips.size());
        assertEquals("one", clips.get(0).name());

        assertTrue(controller.redo());
        assertEquals(1, clips.size());
        assertEquals("two", clips.get(0).name());
    }

    @Test public void moveUndoRedo() {
        List<TimelineClip> clips = new ArrayList<>();
        clips.add(clip("one", 0, 1000));
        clips.add(clip("two", 0, 500));
        FakeProjectState state = new FakeProjectState(clips);
        TimelineController controller = new TimelineController(clips, state, noopListener());

        assertTrue(controller.move(0, 1));
        assertEquals("two", clips.get(0).name());
        assertEquals("one", clips.get(1).name());

        assertTrue(controller.undo());
        assertEquals("one", clips.get(0).name());
        assertEquals("two", clips.get(1).name());

        assertTrue(controller.redo());
        assertEquals("two", clips.get(0).name());
        assertEquals("one", clips.get(1).name());
    }

    @Test public void replaceUndoRedo() {
        List<TimelineClip> clips = new ArrayList<>();
        clips.add(clip("one", 0, 1000));
        clips.add(clip("two", 0, 500));
        FakeProjectState state = new FakeProjectState(clips);
        TimelineController controller = new TimelineController(clips, state, noopListener());
        List<TimelineClip> replacement = new ArrayList<>();
        replacement.add(clip("replacement", 0, 3000));

        assertTrue(controller.replace(replacement, "替换时间线"));
        assertEquals(1, clips.size());
        assertEquals("replacement", clips.get(0).name());

        assertTrue(controller.undo());
        assertEquals(2, clips.size());
        assertEquals("one", clips.get(0).name());

        assertTrue(controller.redo());
        assertEquals(1, clips.size());
        assertEquals("replacement", clips.get(0).name());
    }

    private static TimelineController.Listener noopListener() {
        return new TimelineController.Listener() {
            @Override public void onTimelineChanged() { }
            @Override public void onStatus(String message) { }
        };
    }
}
