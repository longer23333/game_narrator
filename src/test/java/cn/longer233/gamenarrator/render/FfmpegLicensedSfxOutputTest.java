package cn.longer233.gamenarrator.render;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        Assumptions.assumeTrue(available());
        Path output = temporary.resolve("licensed-sfx.m4a");
        String graph = "[0:a]volume=0.15[bg];[1:a]atrim=0:1.5,asetpts=PTS-STARTPTS,volume=0.72,"
                + "afade=t=in:st=0:d=0.2,afade=t=out:st=1.15:d=0.35,adelay=750|750[sfx];"
                + "[bg][sfx]amix=inputs=2:duration=first:normalize=0[aout]";
        Process process = new ProcessBuilder(ffmpeg, "-y", "-f", "lavfi", "-i", "sine=frequency=220:duration=3",
                "-f", "lavfi", "-i", "sine=frequency=880:duration=1.5", "-filter_complex", graph,
                "-map", "[aout]", "-c:a", "aac", output.toString()).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(20, TimeUnit.SECONDS)).as(log).isTrue();
        assertThat(process.exitValue()).as(log).isZero();
        assertThat(Files.size(output)).isGreaterThan(10_000);
    }

    private boolean available() {
        try { return new ProcessBuilder(ffmpeg, "-version").start().waitFor(5, TimeUnit.SECONDS); }
        catch (Exception ignored) { return false; }
    }
}
