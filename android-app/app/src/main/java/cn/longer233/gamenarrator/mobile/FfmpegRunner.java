package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * FFmpeg CLI adapter. The static Android ffmpeg binary is bundled under
 * assets/models/ and copied to the external models directory on first launch.
 */
public final class FfmpegRunner {
    private static final String BIN = "ffmpeg";

    private FfmpegRunner() { }

    public static File binary(Context context) {
        File nativeLibrary = new File(context.getApplicationInfo().nativeLibraryDir, "libffmpeg.so");
        if (nativeLibrary.isFile()) return nativeLibrary;
        return MobileExecutables.internal(context, BIN);
    }

    public static boolean isReady(Context context) {
        return binary(context) != null;
    }

    public static String run(Context context, List<String> arguments) throws Exception {
        File binary = binary(context);
        if (binary == null) throw new IllegalStateException("未检测到 ffmpeg，请把 ffmpeg 放入模型目录");
        List<String> command = new ArrayList<>();
        command.add(binary.getAbsolutePath());
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = readAll(process.getInputStream());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("ffmpeg 执行失败 (exit " + exit + ")"
                    + (output.isBlank() ? "" : "：" + concise(output)));
        }
        return output;
    }

    public static String transcodeToMov(Context context, File input, File output) throws Exception {
        return run(context, Arrays.asList("-y", "-i", input.getAbsolutePath(),
                "-c", "copy", output.getAbsolutePath()));
    }

    public static String transcodeToProRes(Context context, File input, File output) throws Exception {
        return run(context, Arrays.asList("-y", "-i", input.getAbsolutePath(),
                "-c:v", "prores_ks", "-profile:v", "3", "-c:a", "aac",
                output.getAbsolutePath()));
    }

    public static String transcodeWithFilter(Context context, File input, File output, String filter)
            throws Exception {
        String encoder = h264Encoder(context);
        return run(context, Arrays.asList("-y", "-i", input.getAbsolutePath(),
                "-vf", filter, "-c:v", encoder, "-b:v", "8M", "-maxrate", "12M",
                "-bufsize", "16M", "-c:a", "aac",
                output.getAbsolutePath()));
    }

    private static String h264Encoder(Context context) throws Exception {
        String output = run(context, Arrays.asList("-hide_banner", "-encoders"));
        String selected = selectH264Encoder(output);
        if (selected == null) throw new IllegalStateException("ffmpeg 未提供可用的 H.264 编码器");
        return selected;
    }

    static String selectH264Encoder(String encoderOutput) {
        String lower = encoderOutput == null ? "" : encoderOutput.toLowerCase(Locale.ROOT);
        for (String candidate : new String[]{"h264_mediacodec", "h264_omx", "libx264"}) {
            if (lower.contains(candidate)) return candidate;
        }
        return null;
    }

    public static String extractAudio(Context context, File input, File wav) throws Exception {
        return run(context, Arrays.asList("-y", "-i", input.getAbsolutePath(),
                "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le",
                wav.getAbsolutePath()));
    }

    private static String readAll(InputStream input) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String concise(String log) {
        String trimmed = log.trim();
        if (trimmed.length() <= 300) return trimmed;
        return "…" + trimmed.substring(Math.max(0, trimmed.length() - 500));
    }
}
