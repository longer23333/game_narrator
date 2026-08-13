package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TimelineTransitionGraphBuilderTest {
    @Test void createsSynchronizedVideoAndAudioOverlapGraph() {
        var graph = new TimelineTransitionGraphBuilder().build(List.of(
                segment(1, "HARD_CUT", 0, "LEFT", "TRI"),
                segment(2, "PUSH", .3, "RIGHT", "TRI"),
                segment(3, "ANIME_IMPACT", .2, "LEFT", "EXP")));
        assertThat(graph.enabled()).isTrue();
        assertThat(graph.outputDurationSeconds()).isEqualTo(11.5);
        assertThat(graph.filterGraph()).contains("xfade=transition=slideright:duration=0.300:offset=3.700")
                .contains("acrossfade=d=0.300:c1=tri:c2=tri")
                .contains("xfade=transition=radial:duration=0.200:offset=7.500")
                .contains("acrossfade=d=0.200:c1=exp:c2=exp");
    }

    @Test void mixedHardCutAndOverlapRemainSynchronizedInOneGraph() {
        var graph = new TimelineTransitionGraphBuilder().build(List.of(
                segment(1, "HARD_CUT", 0, "LEFT", "TRI"),
                segment(2, "DISSOLVE", .4, "LEFT", "TRI"),
                segment(3, "HARD_CUT", 0, "LEFT", "TRI")));
        assertThat(graph.enabled()).isTrue();
        assertThat(graph.filterGraph()).contains("concat=n=2:v=1:a=1").contains("xfade=transition=fade");
    }

    @Test void fallbackTrimsOverlapWithoutChangingTimelineDuration() {
        var graph = new TimelineTransitionGraphBuilder().hardCutFallback(List.of(
                segment(1, "HARD_CUT", 0, "LEFT", "TRI"),
                segment(2, "PUSH", .3, "RIGHT", "TRI"),
                segment(3, "ANIME_IMPACT", .2, "LEFT", "EXP")));
        assertThat(graph.outputDurationSeconds()).isEqualTo(11.5);
        assertThat(graph.filterGraph()).contains("trim=start=0.300", "atrim=start=0.300",
                "trim=start=0.200", "concat=n=3:v=1:a=1");
    }

    private TimelineSegment segment(int sequence, String type, double duration, String direction, String curve) {
        double start = (sequence - 1) * 4;
        return new TimelineSegment(sequence, start, start + 4, start, start + 4, "", "", "", "voice.wav",
                1, false, type, duration, direction, curve);
    }
}
