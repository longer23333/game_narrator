package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaFormatPreferenceTest {
    @Test public void matchesByExtensionAndMime() {
        assertTrue(MediaFormatPreference.matches("https://x/a.mp4", "video/mp4", MediaFormatPreference.MP4));
        assertTrue(MediaFormatPreference.matches("https://x/a.MOV?x=1", "video/quicktime", MediaFormatPreference.MOV));
        assertTrue(MediaFormatPreference.matches("https://x/a.mp3", "audio/mpeg", MediaFormatPreference.AUDIO));
        assertFalse(MediaFormatPreference.matches("https://x/a.mp4", "video/mp4", MediaFormatPreference.WEBM));
        assertTrue(MediaFormatPreference.matches("https://x/anything", "", MediaFormatPreference.AUTO));
    }
}
