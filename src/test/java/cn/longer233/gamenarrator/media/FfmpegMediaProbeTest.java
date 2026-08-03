package cn.longer233.gamenarrator.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegMediaProbeTest {

    @Test
    void parsesDurationVideoAndAudioStreams() {
        String output = """
                Input #0, mov,mp4,m4a,3gp,3g2,mj2, from 'demo.mp4':
                  Duration: 00:12:34.56, start: 0.000000, bitrate: 12000 kb/s
                  Stream #0:0: Video: h264 (High), yuv420p, 1920x1080, 60 fps, 60 tbr
                  Stream #0:1: Audio: aac (LC), 48000 Hz, stereo, fltp
                """;

        FfmpegMediaProbe probe = new FfmpegMediaProbe("ffmpeg");
        MediaMetadata metadata = probe.parse(output);

        assertThat(metadata.durationSeconds()).isEqualTo(754.56);
        assertThat(metadata.width()).isEqualTo(1920);
        assertThat(metadata.height()).isEqualTo(1080);
        assertThat(metadata.framesPerSecond()).isEqualTo(60);
        assertThat(metadata.videoCodec()).isEqualTo("h264");
        assertThat(metadata.audioCodec()).isEqualTo("aac");
    }
}
