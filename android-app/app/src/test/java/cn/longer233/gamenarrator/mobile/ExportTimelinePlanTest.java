package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;

import android.net.Uri;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class ExportTimelinePlanTest {
    @Test public void mainVideoStartsSkipNonV1ClipsOnGlobalTimeline() {
        List<TimelineClip> clips = new ArrayList<>();
        clips.add(v1("one", 0, 1000));
        clips.add(v2("overlay", 0, 500));
        clips.add(v1("three", 0, 700));
        clips.add(a1("music", 0, 300));
        clips.add(v1("five", 0, 900));

        List<Long> starts = ExportTimelinePlan.mainVideoStartMs(clips);

        assertEquals(Arrays.asList(0L, 1500L, 2500L), starts);
        assertEquals(3400L, ExportTimelinePlan.totalDurationMs(clips));
    }

    @Test public void emptyAndNullInputsStaySafe() {
        assertEquals(0, ExportTimelinePlan.mainVideoStartMs(null).size());
        assertEquals(0L, ExportTimelinePlan.totalDurationMs(null));
        assertEquals(0, ExportTimelinePlan.mainVideoStartMs(new ArrayList<>()).size());
        assertEquals(0L, ExportTimelinePlan.totalDurationMs(new ArrayList<>()));
    }

    private static TimelineClip v1(String name, long start, long end) {
        TimelineClip clip = new TimelineClip("v1-" + name, Uri.parse("content://media/" + name), name, start, end);
        clip.setTrack("V1");
        return clip;
    }

    private static TimelineClip v2(String name, long start, long end) {
        TimelineClip clip = new TimelineClip("v2-" + name, Uri.parse("content://media/" + name), name, start, end);
        clip.setTrack("V2");
        return clip;
    }

    private static TimelineClip a1(String name, long start, long end) {
        TimelineClip clip = new TimelineClip("a1-" + name, Uri.parse("content://media/" + name), name, start, end);
        clip.setTrack("A1");
        return clip;
    }
}
