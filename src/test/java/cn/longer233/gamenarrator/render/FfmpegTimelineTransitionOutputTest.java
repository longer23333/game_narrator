package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;

class FfmpegTimelineTransitionOutputTest {
    @TempDir Path temp;
    private final String ffmpeg = mediaTool("ffmpeg");
    private final String ffprobe = mediaTool("ffprobe");

    @Test void ffmpegProducesPlayableAudioVideoFromParameterizedTransition() throws Exception {
        assertThat(ffmpeg).as("FFmpeg is required for the real transition output test").isNotNull();
        assertThat(ffprobe).as("ffprobe is required for the real transition output test").isNotNull();
        var segments = List.of(segment(1, "HARD_CUT", 0), segment(2, "DISSOLVE", .4));
        var graph = new TimelineTransitionGraphBuilder().build(segments);
        Path first = temp.resolve("first.mp4");
        Path second = temp.resolve("second.mp4");
        createClip(first, "red", 440);
        createClip(second, "blue", 880);
        Path output = temp.resolve("transition.mp4");
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                "-i", first.toString(), "-i", second.toString(), "-filter_complex", graph.filterGraph(),
                "-map", "[vout]", "-map", "[aout]", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", output.toString()));
        var result = ExternalProcessRunner.run(command, Duration.ofMinutes(2), null, ignored -> { });
        assertThat(result.exitCode()).withFailMessage(result.output()).isZero();
        assertThat(output).exists();
        assertThat(Files.size(output)).isGreaterThan(10_000);
        assertPlayableAudioVideo(output, graph.outputDurationSeconds());
    }

    @Test void ffmpegProducesSynchronizedHardCutFallbackAfterTransitionFailure() throws Exception {
        assertThat(ffmpeg).as("FFmpeg is required for the real fallback output test").isNotNull();
        assertThat(ffprobe).as("ffprobe is required for the real fallback output test").isNotNull();
        var segments = List.of(segment(1, "HARD_CUT", 0), segment(2, "DISSOLVE", .4));
        var fallback = new TimelineTransitionGraphBuilder().hardCutFallback(segments);
        Path first = temp.resolve("fallback-first.mp4");
        Path second = temp.resolve("fallback-second.mp4");
        createClip(first, "green", 330);
        createClip(second, "yellow", 660);
        Path output = temp.resolve("fallback.mp4");
        var result = ExternalProcessRunner.run(List.of(ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
                "-i", first.toString(), "-i", second.toString(), "-filter_complex", fallback.filterGraph(),
                "-map", "[vout]", "-map", "[aout]", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", output.toString()), Duration.ofMinutes(2), null, ignored -> { });
        assertThat(result.exitCode()).withFailMessage(result.output()).isZero();
        assertPlayableAudioVideo(output, fallback.outputDurationSeconds());
    }

    private void assertPlayableAudioVideo(Path output, double expectedDuration) throws Exception {
        var probe = ExternalProcessRunner.run(List.of(ffprobe, "-v", "error", "-show_entries",
                "format=duration:stream=codec_type,duration", "-of", "json", output.toString()),
                Duration.ofSeconds(20), null, ignored -> { });
        assertThat(probe.exitCode()).withFailMessage(probe.output()).isZero();
        var json = new ObjectMapper().readTree(probe.output());
        assertThat(json.path("streams").findValuesAsText("codec_type"))
                .contains("video", "audio");
        double containerDuration = json.path("format").path("duration").asDouble();
        assertThat(containerDuration).isCloseTo(expectedDuration, org.assertj.core.data.Offset.offset(.12));
        double videoDuration = streamDuration(json, "video");
        double audioDuration = streamDuration(json, "audio");
        assertThat(Math.abs(videoDuration - audioDuration)).isLessThanOrEqualTo(.12);
    }

    private double streamDuration(com.fasterxml.jackson.databind.JsonNode probe, String type) {
        for (var stream : probe.path("streams")) if (type.equals(stream.path("codec_type").asText()))
            return stream.path("duration").asDouble();
        throw new AssertionError("Missing " + type + " stream");
    }

    private void createClip(Path output, String color, int frequency) throws Exception {
        var result = ExternalProcessRunner.run(List.of(ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
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
    private String mediaTool(String name) {
        Path bundled = Path.of("tools", "ffmpeg", "bin", name + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : ""))
                .toAbsolutePath().normalize();
        if (Files.isRegularFile(bundled) && commandAvailable(bundled.toString())) return bundled.toString();
        return commandAvailable(name) ? name : null;
    }
    private TimelineSegment segment(int sequence, String type, double duration) {
        double start = (sequence - 1) * 2;
        return new TimelineSegment(sequence, start, start + 2, start, start + 2, "", "", "", "voice.wav", 1,
                false, type, duration, "LEFT", "TRI");
    }
}
