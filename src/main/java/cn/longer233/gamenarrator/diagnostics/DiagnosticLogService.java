package cn.longer233.gamenarrator.diagnostics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

    public String recentForTask(UUID taskId, int requestedLines) {
        if (taskId == null) return recent(requestedLines);
        return recentMatching(Set.of("taskId=" + taskId), requestedLines,
                "当前日志尾部没有该任务的记录。任务编号：" + taskId);
    }

    public String recentForTasks(Collection<UUID> taskIds, int requestedLines) {
        Set<String> markers = taskIds == null ? Set.of() : taskIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(taskId -> "taskId=" + taskId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (markers.isEmpty()) return "当前账号还没有可显示的任务日志。";
        return recentMatching(markers, requestedLines, "当前日志尾部没有该账号任务的记录。");
    }

    private String recentMatching(Set<String> markers, int requestedLines, String emptyMessage) {
        int lines = Math.max(20, Math.min(1000, requestedLines));
        if (!Files.isRegularFile(applicationLog)) return "日志文件尚未生成：" + applicationLog.getFileName();
        try {
            List<String> values = new String(readTail(applicationLog, MAX_TAIL_BYTES), StandardCharsets.UTF_8)
                    .lines().toList();
            Set<Integer> selected = new LinkedHashSet<>();
            for (int index = 0; index < values.size(); index++) {
                String marker = matchingMarker(values.get(index), markers);
                if (marker == null) continue;
                int from = index;
                int to = Math.min(values.size(), index + 13);
                for (int context = from; context < to; context++) {
                    String candidate = values.get(context);
                    if (context > index && candidate.contains("taskId=")
                            && matchingMarker(candidate, markers) == null) break;
                    selected.add(context);
                }
            }
            if (selected.isEmpty()) return emptyMessage;
            List<Integer> indexes = selected.stream().toList();
            return indexes.stream().skip(Math.max(0, indexes.size() - lines))
                    .map(index -> sanitize(values.get(index)))
                    .reduce((left, right) -> left + System.lineSeparator() + right).orElse("");
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取任务诊断日志：" + exception.getMessage(), exception);
        }
    }

    private String matchingMarker(String line, Set<String> markers) {
        if (line == null || !line.contains("taskId=")) return null;
        return markers.stream().filter(line::contains).findFirst().orElse(null);
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
