package cn.longer233.gamenarrator.diagnostics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DiagnosticLogService {
    private static final int MAX_TAIL_BYTES = 512 * 1024;
    private static final int MAX_EXPORT_BYTES_PER_FILE = 5 * 1024 * 1024;
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern HEADER_SECRET = Pattern.compile("(?im)^(authorization|cookie)\\s*[:=]\\s*[^\\r\\n]*");
    private static final String SENSITIVE_KEY =
            "(?:api[-_ ]?key|authorization|cookie|password|secret|client[-_ ]?secret|access[-_ ]?token|refresh[-_ ]?token|session[-_ ]?token)";
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\\\"" + SENSITIVE_KEY + "\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")");
    private static final Pattern ASSIGNMENT_SECRET = Pattern.compile(
            "(?i)(" + SENSITIVE_KEY + ")(\\s*[=:]\\s*)([^\\s,;}]+)");
    private final Path logDirectory;
    private final Path applicationLog;

    public DiagnosticLogService(@Value("${logging.file.name:./logs/game-narrator.log}") String logFile) {
        applicationLog = Path.of(logFile).toAbsolutePath().normalize();
        logDirectory = applicationLog.getParent();
    }

    public String recent(int requestedLines) {
        int lines = Math.max(20, Math.min(1000, requestedLines));
        if (!Files.isRegularFile(applicationLog)) return "日志文件尚未生成：" + applicationLog.getFileName();
        try {
            byte[] tail = readTail(applicationLog, MAX_TAIL_BYTES);
            List<String> values = new String(tail, StandardCharsets.UTF_8).lines().toList();
            return values.stream().skip(Math.max(0, values.size() - lines)).map(this::sanitize)
                    .reduce((left, right) -> left + System.lineSeparator() + right).orElse("");
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取诊断日志：" + exception.getMessage(), exception);
        }
    }

    public byte[] export() {
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            if (Files.isDirectory(logDirectory)) {
                try (var files = Files.list(logDirectory)) {
                    for (Path path : files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".log"))
                            .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                        zip.putNextEntry(new ZipEntry(path.getFileName().toString()));
                        String safe = sanitize(new String(readTail(path, MAX_EXPORT_BYTES_PER_FILE), StandardCharsets.UTF_8));
                        zip.write(safe.getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                    }
                }
            }
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write("GameNarrator 诊断日志（已自动隐藏常见凭据字段）。\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("无法导出诊断日志：" + exception.getMessage(), exception);
        }
    }

    public String sanitize(String value) {
        if (value == null) return "";
        String safe = HEADER_SECRET.matcher(value).replaceAll("$1=***");
        safe = BEARER.matcher(safe).replaceAll("Bearer ***");
        safe = JSON_SECRET.matcher(safe).replaceAll("$1***$3");
        return ASSIGNMENT_SECRET.matcher(safe).replaceAll("$1$2***");
    }

    private byte[] readTail(Path path, int maximumBytes) throws Exception {
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            long start = Math.max(0, file.length() - maximumBytes);
            file.seek(start);
            byte[] data = new byte[(int) (file.length() - start)];
            file.readFully(data);
            return data;
        }
    }
}
