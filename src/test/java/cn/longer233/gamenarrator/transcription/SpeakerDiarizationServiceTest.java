package cn.longer233.gamenarrator.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakerDiarizationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path directory;

    @Test
    void preservesTimestampsAndUsesNativeSpeakerLabels() throws Exception {
        Path detail = write("""
                {"transcription":[
                  {"text":"这波操作可以","speaker":"player-1","offsets":{"from":1250,"to":2875}},
                  {"text":"勇士，请继续前进","speaker":"npc-merchant","start":3.0,"end":4.5}
                ]}
                """);

        Path output = service().analyze(result(detail));
        JsonNode root = mapper.readTree(output.toFile());

        assertThat(root.at("/segments/0/speakerType").asText()).isEqualTo("PLAYER_VOICE");
        assertThat(root.at("/segments/0/startMillis").asLong()).isEqualTo(1250);
        assertThat(root.at("/segments/0/endMillis").asLong()).isEqualTo(2875);
        assertThat(root.at("/segments/1/speakerType").asText()).isEqualTo("GAME_CHARACTER_NARRATOR");
        assertThat(root.path("requiresReview").asBoolean()).isFalse();
        assertThat(mapper.readTree(detail.toFile()).at("/speakerDiarization/path").asText()).isEqualTo(output.toString());
    }

    @Test
    void marksOverlappingDifferentSpeakersAsMultiSpeaker() throws Exception {
        Path detail = write("""
                {"transcription":[
                  {"text":"我先上","speaker":"player-1","start":1.0,"end":3.0},
                  {"text":"等等我","speaker":"player-2","start":2.0,"end":4.0}
                ]}
                """);

        JsonNode root = mapper.readTree(service().analyze(result(detail)).toFile());

        assertThat(root.at("/segments/0/speakerType").asText()).isEqualTo("MULTI_SPEAKER");
        assertThat(root.at("/segments/1/evidence").asText()).isEqualTo("TIMELINE_OVERLAP");
    }

    @Test
    void exposesLowConfidenceFallbackForReview() throws Exception {
        Path detail = write("""
                {"transcription":[{"text":"观众朋友们这波看我操作","timestamps":{"from":"00:00:01,100","to":"00:00:02,900"}}]}
                """);

        JsonNode root = mapper.readTree(service().analyze(result(detail)).toFile());

        assertThat(root.at("/segments/0/speakerType").asText()).isEqualTo("PLAYER_VOICE");
        assertThat(root.at("/segments/0/evidence").asText()).isEqualTo("TEXT_FALLBACK");
        assertThat(root.path("requiresReview").asBoolean()).isTrue();
        assertThat(root.at("/segments/0/startMillis").asLong()).isEqualTo(1100);
    }

    @Test
    void guessedSpeakersDoNotPretendToBeAccurate() throws Exception {
        Path detail = write("""
                {"transcription":[
                  {"text":"观众朋友们这波看我操作","start":1.0,"end":2.0},
                  {"text":"系统提示：任务已完成","start":3.0,"end":4.0},
                  {"text":"今天天气不错","start":5.0,"end":6.0}
                ]}
                """);

        JsonNode root = mapper.readTree(service().analyze(result(detail)).toFile());

        assertThat(root.at("/segments/0/confidence").asDouble()).isEqualTo(0.5);
        assertThat(root.at("/segments/1/confidence").asDouble()).isEqualTo(0.5);
        assertThat(root.at("/segments/2/confidence").asDouble()).isEqualTo(0.5);
        // 三种猜测的置信度必须一致：关键词命中与否不代表识别得更准
        assertThat(root.at("/segments/0/confidenceBasis").asText()).contains("关键词猜测");
        assertThat(root.at("/segments/0/confidenceBasis").asText()).contains("非声纹识别");
        assertThat(root.path("confidencePolicy").asText()).contains("不是声纹识别");
        assertThat(root.path("requiresReview").asBoolean()).isTrue();
    }

    @Test
    void nativeLabelsExplainTheirBasis() throws Exception {
        Path detail = write("""
                {"transcription":[{"text":"这波可以","speaker":"player-1","start":1.0,"end":2.0}]}
                """);

        JsonNode root = mapper.readTree(service().analyze(result(detail)).toFile());

        assertThat(root.at("/segments/0/evidence").asText()).isEqualTo("NATIVE_LABEL");
        assertThat(root.at("/segments/0/confidenceBasis").asText()).contains("Whisper");
    }

    private SpeakerDiarizationService service() { return new SpeakerDiarizationService(mapper); }
    private TranscriptionResult result(Path detail) { return new TranscriptionResult("text", null, null, detail.toString()); }
    private Path write(String json) throws Exception {
        Path detail = directory.resolve("transcript.json");
        Files.writeString(detail, json);
        return detail;
    }
}
