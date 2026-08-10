package cn.longer233.gamenarrator.mobile;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * File IO for .gnproject.json archives through the system content resolver.
 */
public final class ProjectStorage {
    public static final int MAX_ARCHIVE_BYTES = 20 * 1024 * 1024;

    private ProjectStorage() { }

    public static void write(ContentResolver resolver, Uri uri, String json) throws IOException {
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("无法打开归档目标");
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    public static String read(ContentResolver resolver, Uri uri, int maxBytes) throws IOException {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("无法读取项目归档");
            return new String(readLimited(input, maxBytes), StandardCharsets.UTF_8);
        }
    }

    public static String safeFileName(String name) {
        String safe = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "GameNarrator-project" : safe;
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0, read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new IllegalArgumentException("文件超过允许大小 " + (limit / 1024 / 1024) + " MiB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
