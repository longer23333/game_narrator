package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class MobileReleaseNotesTest {
    @Test public void latestVersionIsPresent() {
        List<MobileReleaseNotes.Note> notes = MobileReleaseNotes.notes();
        assertTrue(!notes.isEmpty());
        assertEquals("0.68.0", notes.get(0).version());
        for (MobileReleaseNotes.Note note : notes) {
            assertTrue(!note.title().isBlank());
            assertTrue(!note.body().isBlank());
        }
    }
}
