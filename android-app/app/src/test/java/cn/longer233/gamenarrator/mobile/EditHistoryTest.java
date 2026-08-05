package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class EditHistoryTest {
    @Test public void supportsUndoAndRedo() {
        EditHistory history = new EditHistory();
        EditSnapshot first = new EditSnapshot(0, 1000, false, "一");
        EditSnapshot second = new EditSnapshot(100, 900, true, "二");
        history.push(first);
        assertEquals(first, history.undo(second));
        assertEquals(second, history.redo(first));
    }
}
