package cn.longer233.gamenarrator.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperChunkSupportTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void plansFinalShortChunkWithoutExceedingAudioDuration() {
        var chunks = WhisperChunkSupport.plan(65_000, 20_000);
        assertThat(chunks).extracting(WhisperChunkSupport.Chunk::offsetMillis)
                .containsExactly(0L, 20_000L, 40_000L, 60_000L);
        assertThat(chunks).extracting(WhisperChunkSupport.Chunk::durationMillis)
                .containsExactly(20_000L, 20_000L, 20_000L, 5_000L);
    }

    @Test
    void mergesTextSubtitlesAndTranscriptWindows() throws Exception {
        Path one = directory.resolve("part-1");
        Path two = directory.resolve("part-2");
        write(one, "第一段", "1\n00:00:01,000 --> 00:00:02,000\n第一段\n",
                "{\"transcription\":[{\"text\":\"第一段\",\"offsets\":{\"from\":1000,\"to\":2000}}]}");
        write(two, "第二段", "7\n00:20:01,000 --> 00:20:02,000\n第二段\n",
                "{\"transcription\":[{\"text\":\"第二段\",\"offsets\":{\"from\":1201000,\"to\":1202000}}]}");

        Path output = directory.resolve("transcript");
        WhisperChunkSupport.merge(mapper, List.of(one, two), output);

        assertThat(Files.readString(Path.of(output + ".txt"))).containsSubsequence("第一段", "第二段");
        String subtitle = Files.readString(Path.of(output + ".srt")).replace("\r\n", "\n");
        assertThat(subtitle).contains("1\n00:00:01", "2\n00:20:01");
        assertThat(mapper.readTree(Path.of(output + ".json").toFile()).path("transcription")).hasSize(2);
    }

    private void write(Path prefix, String text, String srt, String json) throws Exception {
        Files.writeString(Path.of(prefix + ".txt"), text);
        Files.writeString(Path.of(prefix + ".srt"), srt);
        Files.writeString(Path.of(prefix + ".json"), json);
    }
}
