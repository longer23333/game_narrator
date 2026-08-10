package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;

import android.net.Uri;
import org.junit.Test;

public class TimelineClipTest {
    @Test public void defaultsToMainVideoTrackAndCopiesTrack() {
        TimelineClip clip = new TimelineClip("k", Uri.parse("content://x"), "片段", 0, 1000);
        assertEquals("V1", clip.track());
        clip.setTrack("A1");
        TimelineClip copy = new TimelineClip(clip);
        assertEquals("A1", copy.track());
        copy.setTrack("");
        assertEquals("V1", copy.track());
    }
}
