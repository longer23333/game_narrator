package cn.longer233.gamenarrator.mobile;

import java.io.File;

public final class ExportRecoveryPolicy {
    public static final String INTERRUPTED_ERROR = "Export interrupted because the app stopped";

    private ExportRecoveryPolicy() { }

    public static boolean shouldMarkFailed(String status) {
        return "PROCESSING".equals(status);
    }

    public static boolean shouldCleanOutput(String status, String error, File output, File moviesDir) {
        if (!"FAILED".equals(status) || !INTERRUPTED_ERROR.equals(error)) return false;
        if (output == null || moviesDir == null || output.getParentFile() == null) return false;
        return output.getParentFile().equals(moviesDir.getAbsoluteFile());
    }
}
