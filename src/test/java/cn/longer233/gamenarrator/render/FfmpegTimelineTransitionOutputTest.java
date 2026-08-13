package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FfmpegTimelineTransitionOutputTest {
    @TempDir Path temp;

    @Test void ffmpegProducesPlayableAudioVideoFromParameterizedTransition() throws Exception {
        Assumptions.assumeTrue(commandAvailable("ffmpeg"), "FFmpeg is required for the real output test");
        var segments = List.of(segment(1, "HARD_CUT", 0), segment(2, "DISSOLVE", .4));
        var graph = new TimelineTransitionGraphBuilder().build(segments);
        Path first = temp.resolve("first.mp4");
        Path second = temp.resolve("second.mp4");
        createClip(first, "red", 440);
        createClip(second, "blue", 880);
        Path output = temp.resolve("transition.mp4");
        List<String> command = new ArrayList<>(List.of("ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", first.toString(), "-i", second.toString(), "-filter_complex", graph.filterGraph(),
                "-map", "[vout]", "-map", "[aout]", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", output.toString()));
        var result = ExternalProcessRunner.run(command, Duration.ofMinutes(2), null, ignored -> { });
        assertThat(result.exitCode()).withFailMessage(result.output()).isZero();
        assertThat(output).exists();
        assertThat(Files.size(output)).isGreaterThan(10_000);
    }

    private void createClip(Path output, String color, int frequency) throws Exception {
        var result = ExternalProcessRunner.run(List.of("ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "color=c=" + color + ":s=320x180:d=2:r=25",
                "-f", "lavfi", "-i", "sine=frequency=" + frequency + ":duration=2:sample_rate=48000",
                "-shortest", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", output.toString()),
                Duration.ofMinutes(1), null, ignored -> { });
        assertThat(result.exitCode()).withFailMessage(result.output()).isZero();
    }

    private boolean commandAvailable(String command) {
        try { return ExternalProcessRunner.run(List.of(command, "-version"), Duration.ofSeconds(5), null,
                ignored -> { }).exitCode() == 0; } catch (Exception ignored) { return false; }
    }
    private TimelineSegment segment(int sequence, String type, double duration) {
        double start = (sequence - 1) * 2;
        return new TimelineSegment(sequence, start, start + 2, start, start + 2, "", "", "", "voice.wav", 1,
                false, type, duration, "LEFT", "TRI");
    }
}
