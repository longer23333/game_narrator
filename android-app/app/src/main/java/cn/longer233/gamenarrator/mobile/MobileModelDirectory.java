package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Detects on-device models and distinguishes APK-bundled copies from files
 * installed or replaced by the user in the app-specific external directory.
 */
public final class MobileModelDirectory {
    public enum Source { BUNDLED, USER_INSTALLED, MISSING }

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

    public static Source source(Context context, String kind) {
        File file = modelFile(modelsDir(context), kind);
        if (file == null) return Source.MISSING;
        return matchesBundledAsset(context.getAssets(), file) ? Source.BUNDLED : Source.USER_INSTALLED;
    }

    static Source source(File dir, String kind, String[] bundledNames) {
        File file = modelFile(dir, kind);
        if (file == null) return Source.MISSING;
        return bundledNames != null && Arrays.asList(bundledNames).contains(file.getName())
                ? Source.BUNDLED : Source.USER_INSTALLED;
    }

    static File modelFile(File dir, String kind) {
        if ("whisper".equals(kind)) return first(dir, "whisper-", ".bin", null);
        if ("embedding".equals(kind)) return first(dir, "text-", ".onnx", true);
        if ("text".equals(kind)) return first(dir, "text-", ".onnx", false);
        if ("vision".equals(kind)) return first(dir, "vision-", ".onnx", null);
        return null;
    }

    private static boolean matchesBundledAsset(AssetManager assets, File file) {
        try {
            String asset = "models/" + file.getName();
            return assets.openFd(asset).getLength() == file.length();
        } catch (IOException ignored) {
            return false;
        }
    }

    private static File first(File dir, String prefix, String suffix, Boolean embedding) {
        if (!dir.isDirectory()) return null;
        File[] files = dir.listFiles((unused, name) -> name.startsWith(prefix) && name.endsWith(suffix)
                && (embedding == null || embedding == name.contains("embedding")));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return files[0];
    }

    private static boolean hasFile(File dir, String prefix, String suffix) {
        if (!dir.isDirectory()) return false;
        File[] files = dir.listFiles((unused, name) -> name.startsWith(prefix) && name.endsWith(suffix));
        return files != null && files.length > 0;
    }
}
