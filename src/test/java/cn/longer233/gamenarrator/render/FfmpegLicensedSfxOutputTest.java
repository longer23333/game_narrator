package cn.longer233.gamenarrator.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegLicensedSfxOutputTest {
    @TempDir Path temporary;
    private final String ffmpeg = Files.isRegularFile(Path.of("tools/ffmpeg/bin/ffmpeg.exe"))
            ? "tools/ffmpeg/bin/ffmpeg.exe" : "ffmpeg";

    @Test
    void ffmpegRendersTimedVolumeAndFadedExternalSfx() throws Exception {
        assertThat(available()).as("FFmpeg is required for the real licensed-SFX output test").isTrue();
        Path output = temporary.resolve("licensed-sfx.wav");
        String graph = "[0:a]volume=0.15[bg];[1:a]atrim=0:1.5,asetpts=PTS-STARTPTS,volume=0.72,"
                + "afade=t=in:st=0:d=0.2,afade=t=out:st=1.15:d=0.35,adelay=750|750[sfx];"
                + "[bg][sfx]amix=inputs=2:duration=first:normalize=0[aout]";
        Process process = new ProcessBuilder(ffmpeg, "-y", "-f", "lavfi", "-i", "sine=frequency=220:duration=3",
                "-f", "lavfi", "-i", "sine=frequency=880:duration=1.5", "-filter_complex", graph,
                "-map", "[aout]", "-c:a", "pcm_s16le", output.toString()).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(20, TimeUnit.SECONDS)).as(log).isTrue();
        assertThat(process.exitValue()).as(log).isZero();
        assertThat(Files.size(output)).isGreaterThan(100_000);

        double backgroundBeforeCue = rms(output, 0.25, 0.20);
        double fadeIn = rms(output, 0.78, 0.08);
        double fullVolume = rms(output, 1.10, 0.20);
        double fadeOut = rms(output, 2.10, 0.08);
        double backgroundAfterCue = rms(output, 2.45, 0.20);

        assertThat(backgroundBeforeCue).isBetween(0.012, 0.015);
        assertThat(fadeIn).isGreaterThan(backgroundBeforeCue * 1.10).isLessThan(fullVolume * 0.55);
        assertThat(fullVolume).isGreaterThan(backgroundBeforeCue * 4.5);
        assertThat(fadeOut).isLessThan(fullVolume * 0.55).isGreaterThan(backgroundAfterCue * 1.10);
        assertThat(backgroundAfterCue).isCloseTo(backgroundBeforeCue, within(0.01));
    }

    private double rms(Path wav, double startSeconds, double durationSeconds) throws Exception {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(wav.toFile())) {
            AudioFormat format = input.getFormat();
            assertThat(format.getSampleSizeInBits()).isEqualTo(16);
            int frameSize = format.getFrameSize();
            input.skipNBytes(Math.round(startSeconds * format.getFrameRate()) * frameSize);
            byte[] bytes = input.readNBytes((int) Math.round(durationSeconds * format.getFrameRate()) * frameSize);
            double squares = 0;
            int samples = 0;
            for (int offset = 0; offset + 1 < bytes.length; offset += 2) {
                short sample = (short) ((bytes[offset] & 0xff) | (bytes[offset + 1] << 8));
                double normalized = sample / 32768d;
                squares += normalized * normalized;
                samples++;
            }
            return Math.sqrt(squares / samples);
        }
    }

    private boolean available() {
        try { return new ProcessBuilder(ffmpeg, "-version").start().waitFor(5, TimeUnit.SECONDS); }
        catch (Exception ignored) { return false; }
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
