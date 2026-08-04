package cn.longer233.gamenarrator.transcription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSubtitleReaderTest {
    @TempDir Path directory;

    @Test
    void readsPlatformSubtitleBeforeWhisperIsNeeded() throws Exception {
        Path video = directory.resolve("video.mp4");
        Files.writeString(video, "placeholder");
        Files.writeString(video.resolveSibling("video.mp4.platform.srt"), """
                1
                00:00:00,000 --> 00:00:02,000
                平台字幕优先
                """);
        TranscriptionResult result = new PlatformSubtitleReader().read(video);
        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("平台字幕优先");
        assertThat(result.subtitlePath()).endsWith("platform.srt");
    }
}
