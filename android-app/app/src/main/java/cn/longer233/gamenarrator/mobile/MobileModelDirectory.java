package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import java.io.File;

/**
 * Detects user-provided on-device model files in the app-specific external
 * models directory. Presence only enables the matching capability; inference
 * engines are expected to consume those files later.
 */
public final class MobileModelDirectory {
    public static final class Presence {
        private final boolean whisper, vision, text;

        public Presence(boolean whisper, boolean vision, boolean text) {
            this.whisper = whisper;
            this.vision = vision;
            this.text = text;
        }

        public boolean whisper() { return whisper; }
        public boolean vision() { return vision; }
        public boolean text() { return text; }
    }

    private MobileModelDirectory() { }

    public static File modelsDir(Context context) {
        return new File(context.getExternalFilesDir(null), "models");
    }

    public static Presence check(Context context) {
        return check(modelsDir(context));
    }

    public static Presence check(File dir) {
        return new Presence(hasFile(dir, "whisper-", ".bin"),
                hasFile(dir, "vision-", ".onnx"),
                hasFile(dir, "text-", ".onnx"));
    }

    private static boolean hasFile(File dir, String prefix, String suffix) {
        if (!dir.isDirectory()) return false;
        File[] files = dir.listFiles((unused, name) -> name.startsWith(prefix) && name.endsWith(suffix));
        return files != null && files.length > 0;
    }
}
