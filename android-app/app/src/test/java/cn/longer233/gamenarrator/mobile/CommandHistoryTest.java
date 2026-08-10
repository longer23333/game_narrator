package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CommandHistoryTest {
    @Test public void undoAndRedoRestoreSnapshots() {
        List<TimelineClip> clips = new ArrayList<>();
        clips.add(clip("a", 0, 1000));
        CommandHistory history = new CommandHistory();
        SnapshotEditCommand command = new SnapshotEditCommand(clips);
        history.push(command);
        clips.clear();
        clips.add(clip("b", 0, 2000));
        command.captureAfter();

        assertTrue(history.undo());
        assertEquals(1, clips.size());
        assertEquals("a", clips.get(0).key());
        assertTrue(history.redo());
        assertEquals("b", clips.get(0).key());
    }

    @Test public void newCommandClearsRedoAndLimitApplies() {
        CommandHistory history = new CommandHistory(2);
        List<TimelineClip> clips = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            clips.clear();
            clips.add(clip("c" + i, i, i + 100));
            SnapshotEditCommand command = new SnapshotEditCommand(clips);
            history.push(command);
            command.captureAfter();
        }
        assertEquals(2, history.size());
        assertTrue(history.undo());
        assertTrue(history.undo());
        assertFalse(history.undo());
    }

    private static TimelineClip clip(String key, long start, long end) {
        TimelineClip value = new TimelineClip(key, Uri.parse("content://x"), key, start, end);
        value.updateCreativeText("", "", "");
        return value;
    }
}
