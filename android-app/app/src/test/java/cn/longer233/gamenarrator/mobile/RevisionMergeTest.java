package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RevisionMergeTest {
    @Test public void diffDetectsAddedRemovedAndChanged() {
        TimelineClip a = clip("a", 0, 1000, "字幕A");
        TimelineClip b = clip("b", 1000, 2000, "字幕B");
        TimelineClip c = clip("c", 2000, 3000, "字幕C");
        TimelineClip bChanged = clip("b", 1200, 2000, "字幕B修改");

        RevisionMerge.Diff diff = RevisionMerge.diff(List.of(a, b), List.of(bChanged, c));
        assertEquals(1, diff.added().size());
        assertEquals("c", diff.added().get(0).key());
        assertEquals(1, diff.removed().size());
        assertEquals("a", diff.removed().get(0).key());
        assertEquals(1, diff.changed().size());
        assertEquals("b", diff.changed().get(0).key());
    }

    @Test public void unionMergeAppendsMissingAndKeepsCurrentChanges() {
        TimelineClip a = clip("a", 0, 1000, "当前字幕A");
        TimelineClip b = clip("b", 1000, 2000, "当前字幕B");
        TimelineClip bHistorical = clip("b", 1100, 2000, "历史字幕B");
        TimelineClip c = clip("c", 2000, 3000, "历史字幕C");

        List<TimelineClip> merged = RevisionMerge.merge(List.of(a, b), List.of(bHistorical, c), RevisionMerge.Mode.UNION);
        assertEquals(3, merged.size());
        assertEquals("a", merged.get(0).key());
        assertEquals("b", merged.get(1).key());
        assertEquals("当前字幕B", merged.get(1).subtitle());
        assertEquals("c", merged.get(2).key());
    }

    @Test public void overwriteMergeReplacesMatchingKey() {
        TimelineClip b = clip("b", 1000, 2000, "当前字幕B");
        TimelineClip bHistorical = clip("b", 1100, 2000, "历史字幕B");

        List<TimelineClip> merged = RevisionMerge.merge(List.of(b), List.of(bHistorical), RevisionMerge.Mode.OVERWRITE);
        assertEquals(1, merged.size());
        assertEquals(1100, merged.get(0).startMs());
        assertEquals("历史字幕B", merged.get(0).subtitle());
    }

    @Test public void mergePreservesCurrentOrder() {
        TimelineClip a = clip("a", 0, 1000, "");
        TimelineClip b = clip("b", 1000, 2000, "");
        TimelineClip c = clip("c", 2000, 3000, "");
        List<TimelineClip> merged = RevisionMerge.merge(List.of(b, a), List.of(c, a), RevisionMerge.Mode.UNION);
        List<String> keys = new ArrayList<>();
        for (TimelineClip clip : merged) keys.add(clip.key());
        assertEquals(List.of("b", "a", "c"), keys);
        assertEquals(3, merged.size());
    }

    private static TimelineClip clip(String key, long start, long end, String subtitle) {
        TimelineClip value = new TimelineClip(key, Uri.parse("content://test/" + key), key, start, end);
        value.updateCreativeText(subtitle, "解说", "");
        return value;
    }
}
