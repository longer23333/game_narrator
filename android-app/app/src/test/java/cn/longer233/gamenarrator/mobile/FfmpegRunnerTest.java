package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class FfmpegRunnerTest {
    @Test public void prefersAndroidHardwareEncoders() {
        String sample = " V..... h264_mediacodec           Android MediaCodec H.264 encoder\n"
                + " V..... libx264                  libx264 H.264/MPEG-4 AVC encoder\n";
        assertEquals("h264_mediacodec", FfmpegRunner.selectH264Encoder(sample));
    }

    @Test public void fallsBackToLibX264WhenHardwareMissing() {
        String sample = " V..... libx264                  libx264 H.264/MPEG-4 AVC encoder\n";
        assertEquals("libx264", FfmpegRunner.selectH264Encoder(sample));
    }

    @Test public void returnsNullWhenNoH264Encoder() {
        assertNull(FfmpegRunner.selectH264Encoder(" V..... mpeg4    MPEG-4 part 2 encoder\n"));
    }
}
