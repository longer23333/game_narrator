package cn.longer233.gamenarrator.subtitle;

import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AssSubtitleBuilderTest {
    private static final Pattern KARAOKE = Pattern.compile("\\\\kf(\\d+)");

    @Test
    void createsAnimatedKaraokeTimelineAndPreservesTheSegmentDuration() {
        var segment = new TimelineSegment(1, 0, 3.5, 10, 13.5,
                "这是用于测试的较长中文解说字幕文本", "这是用于测试的较长中文解说字幕文本",
                "高燃", "voice.wav", 2, false);

        String ass = new AssSubtitleBuilder().build(List.of(segment), "IMPACT_RED");

        assertThat(ass).contains("[V4+ Styles]", "Dialogue: 0,0:00:00.00,0:00:03.50",
                "\\N", "\\t(0,140", "\\kf");
        assertThat(karaokeDuration(ass)).isEqualTo(350);
        assertThat(KARAOKE.matcher(ass).results().count()).isGreaterThan(10);
    }

    @Test
    void highlightsEnglishByWordInsteadOfByLetter() {
        var segment = new TimelineSegment(1, 0, 2, 0, 2,
                "Boss battle starts now", "Boss battle starts now", "", "voice.wav", 1, false);

        String ass = new AssSubtitleBuilder().build(List.of(segment), "CLEAN_WHITE");

        assertThat(ass).contains("}Boss ", "}battle ", "}starts ", "}now");
        assertThat(KARAOKE.matcher(ass).results().count()).isEqualTo(4);
        assertThat(karaokeDuration(ass)).isEqualTo(200);
    }

    private int karaokeDuration(String ass) {
        Matcher matcher = KARAOKE.matcher(ass);
        int result = 0;
        while (matcher.find()) result += Integer.parseInt(matcher.group(1));
        return result;
    }
}
