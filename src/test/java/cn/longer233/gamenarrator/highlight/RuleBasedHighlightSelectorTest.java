package cn.longer233.gamenarrator.highlight;

import cn.longer233.gamenarrator.vision.FrameUnderstanding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedHighlightSelectorTest {
    @TempDir Path tempDir;

    @Test
    void keepsTheWholeVideoAndUsesBestFrameAsEachSegmentAnchor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path input = tempDir.resolve("visual-analysis.json");
        List<FrameUnderstanding> frames = List.of(
                frame(1, 10, "探索", 70),
                frame(2, 14, "战斗", 80),
                frame(3, 50, "胜利", 75),
                frame(4, 90, "其他", 20));
        mapper.writeValue(input.toFile(), Map.of("frames", frames));

        HighlightSelectionResult result = new RuleBasedHighlightSelector(mapper)
                .select(input, 100, 24);

        assertThat(result.clips()).hasSize(4);
        assertThat(result.clips()).extracting(HighlightClip::sourceFrameIndex).containsExactly(2, 3, 4, 4);
        assertThat(result.clips().getFirst().startSeconds()).isZero();
        assertThat(result.clips().getLast().endSeconds()).isEqualTo(100);
        assertThat(result.clips()).extracting(HighlightClip::durationSeconds).containsExactly(30.0, 30.0, 30.0, 10.0);
        assertThat(Path.of(result.manifestPath())).exists();
        assertThat(Files.readString(Path.of(result.manifestPath()))).contains("full-story-v1");
    }

    @Test
    void stillSelectsCandidatesWhenAllVisionScoresAreZero() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path input = tempDir.resolve("visual-analysis.json");
        mapper.writeValue(input.toFile(), Map.of("frames", List.of(
                frame(1, 5, "其他", 0), frame(2, 30, "其他", 0))));

        HighlightSelectionResult result = new RuleBasedHighlightSelector(mapper)
                .select(input, 40, 12);

        assertThat(result.clips()).hasSize(2);
        assertThat(result.clips()).extracting(HighlightClip::durationSeconds).containsExactly(30.0, 10.0);
    }

    @Test
    void canCreateARealHighlightsOnlyCutWithinTheRequestedBudget() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path input = tempDir.resolve("visual-analysis.json");
        mapper.writeValue(input.toFile(), Map.of("frames", List.of(
                frame(1, 10, "探索", 20), frame(2, 45, "战斗", 95), frame(3, 90, "胜利", 90))));

        HighlightSelectionResult result = new RuleBasedHighlightSelector(mapper)
                .select(input, 120, 40, "HIGHLIGHTS");

        assertThat(result.clips()).hasSize(2);
        assertThat(result.clips()).extracting(HighlightClip::sourceFrameIndex).containsExactlyInAnyOrder(2, 3);
        assertThat(result.clips()).allMatch(clip -> clip.durationSeconds() <= 20);
        assertThat(Files.readString(Path.of(result.manifestPath()))).contains("ranked-highlights-v1", "HIGHLIGHTS");
    }

    @Test
    void aiContentHintCanPromoteSemanticallyImportantMoment() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path input = tempDir.resolve("visual-analysis.json");
        mapper.writeValue(input.toFile(), Map.of(
                "contentAnalysis", Map.of(
                        "overview", "玩家完成关键解谜。",
                        "highlightStrategy", "保留谜题揭晓时刻。",
                        "highlightHints", List.of(Map.of(
                                "timestampSeconds", 40, "reason", "谜题揭晓", "importance", 100))),
                "frames", List.of(frame(1, 10, "其他", 60), frame(2, 40, "其他", 45))));

        HighlightSelectionResult result = new RuleBasedHighlightSelector(mapper).select(input, 60, 12);

        assertThat(result.clips()).extracting(HighlightClip::sourceFrameIndex).containsExactly(1, 2);
        assertThat(Files.readString(Path.of(result.manifestPath()))).contains("ai-guided-full-story-v1", "谜题揭晓");
    }

    private FrameUnderstanding frame(int index, double time, String event, int score) {
        return new FrameUnderstanding(index, time, "frame.jpg", "description", event, score, "{}");
    }
}
