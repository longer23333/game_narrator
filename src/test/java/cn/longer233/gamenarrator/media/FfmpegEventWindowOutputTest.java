package cn.longer233.gamenarrator.media;

import cn.longer233.gamenarrator.audio.AudioAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegEventWindowOutputTest {
    @TempDir Path temporary;
    private final String ffmpeg = Files.isRegularFile(Path.of("tools/ffmpeg/bin/ffmpeg.exe"))
            ? "tools/ffmpeg/bin/ffmpeg.exe" : "ffmpeg";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void highEnergyPulseProducesRealPreCenterAndPostEventFrames() throws Exception {
        assertThat(available()).as("FFmpeg is required for the real event-window output test").isTrue();
        Path source = temporary.resolve("pulse.mp4");
        Process process = new ProcessBuilder(ffmpeg, "-y", "-f", "lavfi", "-i",
                "color=c=navy:s=320x180:r=10:d=4", "-f", "lavfi", "-i",
                "aevalsrc=if(between(t\\,1.8\\,1.95)\\,0.98*sin(2*PI*880*t)\\,0.02*sin(2*PI*220*t)):s=16000:d=4",
                "-shortest", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", source.toString())
                .redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(20, TimeUnit.SECONDS)).as(log).isTrue();
        assertThat(process.exitValue()).as(log).isZero();

        var preprocessor = new FfmpegMediaPreprocessor(ffmpeg, temporary.toString(), .35, 6, 40,
                2, 8, .35, mapper, new AudioAnalysisService(ffmpeg, mapper));
        MediaPreparationResult result = preprocessor.prepare(UUID.randomUUID(), source, true);

        List<SceneFrame> eventFrames = result.scenes().stream()
                .filter(frame -> frame.samplingReason() != null && frame.samplingReason().startsWith("AUDIO_"))
                .toList();
        assertThat(eventFrames).hasSizeGreaterThanOrEqualTo(3);
        double anchor = eventFrames.getFirst().eventAnchorSeconds();
        assertThat(anchor).isBetween(1.0, 2.2);
        assertThat(eventFrames).anyMatch(frame -> frame.timestampSeconds() < anchor)
                .anyMatch(frame -> Math.abs(frame.timestampSeconds() - anchor) < .08)
                .anyMatch(frame -> frame.timestampSeconds() > anchor);
        assertThat(eventFrames).allMatch(frame -> Files.isRegularFile(Path.of(frame.imagePath()))
                && fileSize(frame.imagePath()) > 1_000);
        assertThat(Files.isRegularFile(Path.of(result.sceneManifestPath()))).isTrue();
    }

    private long fileSize(String path) {
        try { return Files.size(Path.of(path)); }
        catch (Exception ignored) { return -1; }
    }

    private boolean available() {
        try { return new ProcessBuilder(ffmpeg, "-version").start().waitFor(5, TimeUnit.SECONDS); }
        catch (Exception ignored) { return false; }
    }
}
