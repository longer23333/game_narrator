package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ShotTextIndexTest {
    @Test public void findsCjkSubtitleAndRanksByMatchedFields() {
        List<ShotTextIndex.Segment> segments = Arrays.asList(
                new ShotTextIndex.Segment("a", "开场", "主角在废墟中寻找答案", "战斗结束后的低语", ""),
                new ShotTextIndex.Segment("b", "结尾", "天空逐渐放晴", "主角离开废墟", "高对比"));
        List<ShotTextIndex.Hit> hits = ShotTextIndex.search(segments, "废墟", 5);
        assertEquals(2, hits.size());
        assertEquals("a", hits.get(0).key());
        assertTrue(hits.get(0).fields().contains("字幕"));
        assertTrue(hits.get(1).fields().contains("解说"));
    }

    @Test public void blankQueryReturnsNoHits() {
        List<ShotTextIndex.Segment> segments = Arrays.asList(
                new ShotTextIndex.Segment("a", "开场", "字幕", "解说", "特效"));
        assertTrue(ShotTextIndex.search(segments, "   ", 5).isEmpty());
        assertTrue(ShotTextIndex.search(segments, "", 5).isEmpty());
    }

    @Test public void respectsMaxResultsAndMatchesEffectCue() {
        List<ShotTextIndex.Segment> segments = Arrays.asList(
                new ShotTextIndex.Segment("a", "第一镜", "普通字幕", "普通解说", ""),
                new ShotTextIndex.Segment("b", "第二镜", "普通字幕", "普通解说", "高对比黑白转场"),
                new ShotTextIndex.Segment("c", "第三镜", "普通字幕", "普通解说", "冷色"));
        List<ShotTextIndex.Hit> hits = ShotTextIndex.search(segments, "黑白转场", 1);
        assertEquals(1, hits.size());
        assertEquals("b", hits.get(0).key());
        assertTrue(hits.get(0).fields().contains("特效"));
    }
}
