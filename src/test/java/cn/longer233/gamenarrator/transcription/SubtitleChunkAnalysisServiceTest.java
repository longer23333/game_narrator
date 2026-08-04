package cn.longer233.gamenarrator.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SubtitleChunkAnalysisServiceTest {
    @TempDir Path directory;

    @Test
    void createsOrderedOutlineTimelineAndExcitementPasses() throws Exception {
        Path subtitle = directory.resolve("source.srt");
        Files.writeString(subtitle, """
                1
                00:00:01,000 --> 00:00:03,000
                开始探索地图

                2
                00:00:08,000 --> 00:00:10,000
                精彩反杀！获得胜利！
                """);
        ObjectMapper mapper = new ObjectMapper();
        Path output = new SubtitleChunkAnalysisService(mapper, 500).analyze(subtitle);
        var root = mapper.readTree(output.toFile());
        assertThat(root.path("strategy").asText()).isEqualTo("chunked-subtitle-v1");
        assertThat(root.path("chunks").get(0).path("outline").asText()).contains("开始探索");
        assertThat(root.path("chunks").get(0).path("timeline").path("startSeconds").asDouble()).isEqualTo(1);
        assertThat(root.path("chunks").get(0).path("excitementScore").asInt()).isGreaterThan(35);
    }
}
