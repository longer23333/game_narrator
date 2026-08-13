package cn.longer233.gamenarrator.render;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import javax.imageio.ImageIO;
import java.awt.Color;

class FfmpegMemeOverlayOutputTest {
    @TempDir Path temporary;
    private final String ffmpeg = Files.isRegularFile(Path.of("tools/ffmpeg/bin/ffmpeg.exe"))
            ? "tools/ffmpeg/bin/ffmpeg.exe" : "ffmpeg";

    @Test
    void ffmpegPreservesTransparentPngAndWebpInTimedLayeredOverlay() throws Exception {
        Assumptions.assumeTrue(available());
        Path png = temporary.resolve("lower.png");
        Path webp = temporary.resolve("upper.webp");
        Path output = temporary.resolve("meme-overlay.mp4");
        run(List.of(ffmpeg, "-y", "-f", "lavfi", "-i", "color=c=red@0.65:s=180x180:d=1,format=rgba",
                "-frames:v", "1", png.toString()));
        run(List.of(ffmpeg, "-y", "-f", "lavfi", "-i", "color=c=blue@0.65:s=140x140:d=1,format=rgba",
                "-frames:v", "1", webp.toString()));
        String filter = "[0:v]format=rgba[base];[1:v]format=rgba,fade=t=in:st=0.4:d=0.2:alpha=1[p];"
                + "[base][p]overlay=40:40:enable='between(t,0.4,2.4)':eof_action=pass:shortest=0[v1];"
                + "[2:v]format=rgba[w];[v1][w]overlay=60:60:enable='between(t,1,2)':eof_action=pass:shortest=0,format=yuv420p[vout]";
        Process process = new ProcessBuilder(ffmpeg, "-y", "-f", "lavfi", "-i", "color=c=black:s=640x360:d=3:r=25",
                "-loop", "1", "-i", png.toString(), "-loop", "1", "-i", webp.toString(),
                "-filter_complex", filter, "-map", "[vout]", "-t", "3", "-c:v", "libx264", output.toString())
                .redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as(log).isTrue();
        assertThat(process.exitValue()).as(log).isZero();
        assertThat(output).exists();
        assertThat(Files.size(output)).isGreaterThan(3_000);
        assertDark(pixel(output, .2, 80, 80));
        assertRed(pixel(output, .8, 80, 80));
        assertBlue(pixel(output, 1.5, 80, 80));
        assertDark(pixel(output, 2.7, 80, 80));
    }

    private Color pixel(Path video, double seconds, int x, int y) throws Exception {
        Path frame = temporary.resolve("frame-" + String.valueOf(seconds).replace('.', '-') + ".png");
        run(List.of(ffmpeg, "-y", "-ss", Double.toString(seconds), "-i", video.toString(),
                "-frames:v", "1", frame.toString()));
        return new Color(ImageIO.read(frame.toFile()).getRGB(x, y));
    }
    private void assertDark(Color color) { assertThat(color.getRed()+color.getGreen()+color.getBlue()).isLessThan(45); }
    private void assertRed(Color color) { assertThat(color.getRed()).isGreaterThan(color.getBlue()+40); }
    private void assertBlue(Color color) { assertThat(color.getBlue()).isGreaterThan(color.getRed()+40); }

    private boolean available() {
        try { return new ProcessBuilder(ffmpeg, "-version").start().waitFor(5, TimeUnit.SECONDS); }
        catch (Exception ignored) { return false; }
    }
    private void run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(20, TimeUnit.SECONDS)).as(log).isTrue();
        assertThat(process.exitValue()).as(log).isZero();
    }
}
