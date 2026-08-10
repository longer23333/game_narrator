package cn.longer233.gamenarrator.mobile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public final class StructuredLog {
    public static final int MAX_ENTRIES = 2000;
    public static final long MAX_BYTES = 1024L * 1024L;

    private StructuredLog() { }

    public static void append(File file, long timestampMs, String level, String source, String message) throws IOException {
        if (file == null || level == null || source == null || message == null) {
            throw new IllegalArgumentException("log fields must not be null");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create log directory");
        }
        String line = jsonLine(timestampMs, level, source, message) + System.lineSeparator();
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(line.getBytes(StandardCharsets.UTF_8));
        }
        if (count(file) > MAX_ENTRIES || file.length() > MAX_BYTES) {
            compact(file);
        }
    }

    public static String readAll(File file) throws IOException {
        if (file == null || !file.isFile()) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    public static List<String> tail(File file, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();
        if (file == null || !file.isFile() || maxLines <= 0) return lines;
        Deque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                tail.addLast(line);
                while (tail.size() > maxLines) tail.removeFirst();
            }
        }
        return new ArrayList<>(tail);
    }

    public static int count(File file) {
        if (file == null || !file.isFile()) return 0;
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) count++;
        } catch (IOException ignored) { }
        return count;
    }

    public static boolean clear(File file) {
        return file != null && file.delete();
    }

    public static void compact(File file) throws IOException {
        if (file == null || !file.isFile()) return;
        List<String> kept = tail(file, MAX_ENTRIES);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            for (String line : kept) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        }
    }

    private static String jsonLine(long timestampMs, String level, String source, String message) {
        return "{\"ts\":" + timestampMs
                + ",\"level\":\"" + escape(level)
                + "\",\"source\":\"" + escape(source)
                + "\",\"message\":\"" + escape(message) + "\"}";
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': builder.append("\\\""); break;
                case '\\': builder.append("\\\\"); break;
                case '\b': builder.append("\\b"); break;
                case '\f': builder.append("\\f"); break;
                case '\n': builder.append("\\n"); break;
                case '\r': builder.append("\\r"); break;
                case '\t': builder.append("\\t"); break;
                default:
                    if (c < 0x20) builder.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else builder.append(c);
            }
        }
        return builder.toString();
    }
}
