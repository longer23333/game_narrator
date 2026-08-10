package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Whisper.cpp adapter. The model file and the platform whisper-cli executable
 * are both user-provided in the external models directory; neither is bundled
 * unless the user chooses to put them under assets/models/.
 */
public final class WhisperModelRunner {
    private static final String ENGINE_LINUX = "whisper-cli";
    private static final String ENGINE_WINDOWS = "whisper-cli.exe";

    private WhisperModelRunner() { }

    public static File modelFile(Context context) {
        return modelFile(MobileModelDirectory.modelsDir(context));
    }

    static File modelFile(File dir) {
        return first(dir, "whisper-", ".bin");
    }

    public static File engineFile(Context context) {
        File internal = MobileExecutables.internal(context, ENGINE_LINUX);
        if (internal != null) return internal;
        return MobileExecutables.internal(context, ENGINE_WINDOWS);
    }

    static File engineFile(File dir) {
        File linux = new File(dir, ENGINE_LINUX);
        if (linux.isFile()) return linux;
        File windows = new File(dir, ENGINE_WINDOWS);
        return windows.isFile() ? windows : null;
    }

    public static boolean isReady(Context context) {
        return modelFile(context) != null && engineFile(context) != null;
    }

    static boolean isReady(File dir) {
        return modelFile(dir) != null && engineFile(dir) != null;
    }

    public static String transcribe(Context context, File wav) throws IOException, InterruptedException {
        File dir = MobileModelDirectory.modelsDir(context);
        File model = modelFile(dir);
        if (model == null) {
            throw new IllegalStateException("未检测到 whisper-*.bin，请先放入模型目录");
        }
        File engine = engineFile(context);
        if (engine == null) {
            throw new IllegalStateException("未检测到 whisper-cli 引擎，请把 whisper.cpp Android 可执行文件放入模型目录");
        }
        File outputDir = new File(context.getCacheDir(), "whisper");
        if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
            throw new IOException("无法创建转写临时目录");
        }
        return transcribe(engine, model, wav, outputDir);
    }

    static String transcribe(File engine, File model, File wav, File outputDir)
            throws IOException, InterruptedException {
        String base = new File(outputDir, "transcript").getAbsolutePath();
        List<String> command = new ArrayList<>();
        command.add(engine.getAbsolutePath());
        command.add("-m");
        command.add(model.getAbsolutePath());
        command.add("-f");
        command.add(wav.getAbsolutePath());
        command.add("-l");
        command.add("zh");
        command.add("-otxt");
        command.add("-of");
        command.add(base);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String log = readAll(process.getInputStream());
        int exit = process.waitFor();
        File text = new File(base + ".txt");
        if (exit != 0 || !text.isFile()) {
            throw new IOException("whisper-cli 转写失败 (exit " + exit + ")"
                    + (log.isBlank() ? "" : "：" + concise(log)));
        }
        return new String(Files.readAllBytes(text.toPath()), StandardCharsets.UTF_8).trim();
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
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }

    private static File first(File dir, String prefix, String suffix) {
        if (!dir.isDirectory()) return null;
        File[] files = dir.listFiles((unused, name) -> name.startsWith(prefix) && name.endsWith(suffix));
        return files != null && files.length > 0 ? files[0] : null;
    }
}
