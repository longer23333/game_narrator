package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copies CLI executables from the external models directory into the app's
 * internal bin directory, where Android permits execution.
 */
public final class MobileExecutables {
    private MobileExecutables() { }

    public static File internal(Context context, String name) {
        File source = new File(MobileModelDirectory.modelsDir(context), name);
        if (!source.isFile()) return null;
        File dir = new File(context.getFilesDir(), "bin");
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File target = new File(dir, name);
        if (!target.isFile()) {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            } catch (IOException ignored) {
                return null;
            }
        }
        makeExecutable(target);
        return target;
    }

    private static void makeExecutable(File file) {
        file.setExecutable(true, true);
        file.setExecutable(true, false);
        try {
            Process process = new ProcessBuilder("/system/bin/chmod", "755",
                    file.getAbsolutePath()).start();
            process.waitFor();
        } catch (Exception ignored) { }
    }
}
