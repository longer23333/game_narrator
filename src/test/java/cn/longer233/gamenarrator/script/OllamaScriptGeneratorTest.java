package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.highlight.HighlightClip;
import cn.longer233.gamenarrator.event.GameEventFact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaScriptGeneratorTest {
    @Test
    void alignsModelOutputToEveryHighlightAndFillsMissingSegments() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OllamaScriptGenerator generator = new OllamaScriptGenerator(mapper,
                "http://localhost:11434", "test-model");
        var generated = mapper.readTree("""
                [{"narration":"第一段台词","subtitle":"第一幕","effectCue":"震动"}]
                """);
        List<HighlightClip> clips = List.of(
                clip(1, 0, 12), clip(2, 20, 32));

        List<ScriptSegment> result = generator.alignSegments(generated, clips);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().narration()).isEqualTo("第一段台词");
        assertThat(result.getFirst().subtitle()).isEqualTo("第一幕");
        assertThat(result.get(1).narration()).isNotBlank();
        assertThat(result.get(1).subtitle()).isNotBlank();
        assertThat(result.get(1).effectCue()).isNotBlank();
        assertThat(result.get(1).startSeconds()).isEqualTo(20);
    }

    @Test
    void keepsModelQualityReviewAndBoundsItsScore() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OllamaScriptGenerator generator = new OllamaScriptGenerator(mapper,
                "http://localhost:11434", "test-model");
        var generated = mapper.readTree("""
                {"qualityReview":{"score":120,"passed":true,"issues":["字幕略长"],"summary":"建议复核"}}
                """);
        var review = generator.qualityReview(generated,
                List.of(new ScriptSegment(1, 0, 5, "解说", "字幕", "转场")));

        assertThat(review).containsEntry("score", 100).containsEntry("summary", "建议复核");
        assertThat(review.get("issues")).isEqualTo(List.of("字幕略长"));
    }

    @Test
    void independentReviewPromptContainsManualFeedbackAndLocalDurationCheckCannotPass() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OllamaScriptGenerator generator = new OllamaScriptGenerator(mapper,
                "http://localhost:11434", "test-model");
        ScriptDocumentView document = new ScriptDocumentView("title", "summary", "very long narration",
                ScriptQualityReview.unavailable(), List.of(new ScriptSegment(1, 0, 1,
                "这是一段明显超过一秒钟可配音长度的中文解说文案", "字幕", "转场")));

        String prompt = generator.buildQualityReviewPrompt(document,
                Map.of("1", Map.of("status", "NEEDS_CHANGES", "note", "角色名错误")));
        ScriptQualityReview review = generator.parseQualityReview(mapper.readTree(
                "{\"score\":95,\"passed\":true,\"issues\":[],\"summary\":\"模型误判通过\"}"), document.segments());

        assertThat(prompt).contains("角色名错误", "人工评审是最高优先级证据", "片段 N：");
        assertThat(review.passed()).isFalse();
        assertThat(review.issues()).anyMatch(issue -> issue.contains("可配音时长"));
    }

    @Test
    void promptExposesOnlyConfirmedFactsAndHidesUnreviewedClipDescriptions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OllamaScriptGenerator generator = new OllamaScriptGenerator(mapper,
                "http://localhost:11434", "test-model");
        HighlightClip unreviewed = new HighlightClip(1, 10, 20, 15,
                "BOSS_DEFEATED", "未经确认的角色完成击杀", 80, 90);

        String prompt = generator.buildPrompt(List.of(unreviewed), "ACTION", "ANIME_THEATER", "保持连贯", "",
                List.of(new GameEventFact("PHASE_TRANSITION", "Boss 进入第二阶段", 12, 16, 85)));

        assertThat(prompt).contains("Boss 进入第二阶段").contains("只能把“已确认事件事实”中的内容写成确定事实");
        assertThat(prompt).doesNotContain("未经确认的角色完成击杀").doesNotContain("BOSS_DEFEATED");
    }

    private HighlightClip clip(int index, double start, double end) {
        return new HighlightClip(index, start, end, start + 4, "战斗", "画面", 80, 90);
    }
}
