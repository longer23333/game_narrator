package cn.longer233.gamenarrator.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegEncoderCapabilitiesTest {
    @Test
    void usesHardwareWhenRealProbeSucceedsAndCachesResult() {
        List<String> probes = new ArrayList<>();
        FfmpegEncoderCapabilities capabilities = new FfmpegEncoderCapabilities("ffmpeg", (command, encoder) -> {
            probes.add(encoder);
            return new FfmpegEncoderCapabilities.ProbeResult(true, "ok");
        });

        assertThat(capabilities.resolve("H264_NVENC", "libx264")).isEqualTo("h264_nvenc");
        assertThat(capabilities.resolve("h264_nvenc", "libx264")).isEqualTo("h264_nvenc");
        assertThat(probes).containsExactly("h264_nvenc");
    }

    @Test
    void fallsBackOnlyAfterSoftwareEncoderProbeSucceeds() {
        List<String> probes = new ArrayList<>();
        FfmpegEncoderCapabilities capabilities = new FfmpegEncoderCapabilities("ffmpeg", (command, encoder) -> {
            probes.add(encoder);
            return new FfmpegEncoderCapabilities.ProbeResult("libx264".equals(encoder),
                    "libx264".equals(encoder) ? "ok" : "driver mismatch");
        });

        assertThat(capabilities.resolve("h264_nvenc", "libx264")).isEqualTo("libx264");
        assertThat(probes).containsExactly("h264_nvenc", "libx264");
    }

    @Test
    void reportsBothFailuresAndDoesNotProbeSoftwareOnlyRequest() {
        List<String> probes = new ArrayList<>();
        FfmpegEncoderCapabilities capabilities = new FfmpegEncoderCapabilities("ffmpeg", (command, encoder) -> {
            probes.add(encoder);
            return new FfmpegEncoderCapabilities.ProbeResult(false, encoder + " unavailable");
        });

        assertThatThrownBy(() -> capabilities.resolve("h264_nvenc", "libx264"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("h264_nvenc unavailable")
                .hasMessageContaining("libx264 unavailable")
                .hasMessageContaining("显卡驱动");
        assertThat(capabilities.resolve("libx265", "libx265")).isEqualTo("libx265");
        assertThat(probes).containsExactly("h264_nvenc", "libx264");
    }
}
