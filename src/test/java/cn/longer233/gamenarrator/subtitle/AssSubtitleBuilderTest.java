package cn.longer233.gamenarrator.subtitle;

import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssSubtitleBuilderTest {
    @Test
    void createsAnimatedSingleLineAssTimeline() {
        var segment = new TimelineSegment(1, 0, 3.5, 10, 13.5,
                "这是用于测试的较长中文解说字幕文本", "这是用于测试的较长中文解说字幕文本",
                "高燃", "voice.wav", 2, false);

        String ass = new AssSubtitleBuilder().build(List.of(segment), "IMPACT_RED");

        assertThat(ass).contains("[V4+ Styles]", "Dialogue: 0,0:00:00.00,0:00:03.50",
                "\\N", "\\t(0,140");
    }
}
