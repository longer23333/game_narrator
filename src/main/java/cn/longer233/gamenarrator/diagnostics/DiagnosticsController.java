package cn.longer233.gamenarrator.diagnostics;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/debug")
public class DiagnosticsController {

    private final SystemDiagnosticsService diagnostics;
    private final DiagnosticLogService logs;
    private final VideoTaskRepository tasks;
    private final CurrentUserContext current;
    private static final Logger log = LoggerFactory.getLogger(DiagnosticsController.class);

    public DiagnosticsController(SystemDiagnosticsService diagnostics, DiagnosticLogService logs,
                                 VideoTaskRepository tasks, CurrentUserContext current) {
        this.diagnostics = diagnostics;
        this.logs = logs;
        this.tasks = tasks;
        this.current = current;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return diagnostics.inspect();
    }

    @GetMapping(value = "/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String logs(@RequestParam(defaultValue = "300") int lines,
                       @RequestParam(required = false) UUID taskId) {
        if (taskId != null) {
            if (!isAdmin() && !tasks.existsByIdAndOwnerId(taskId, current.userId())) {
                throw new TaskNotFoundException(taskId);
            }
            return logs.recentForTask(taskId, lines);
        }
        if (current.authenticated() && !isAdmin()) {
            return logs.recentForTasks(tasks.findIdsByOwnerIdOrderByCreatedAtDesc(current.userId()), lines);
        }
        return logs.recent(lines);
    }

    private boolean isAdmin() { return current.authenticated() && "ADMIN".equals(current.role()); }

    @GetMapping(value = "/logs/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportLogs() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=GameNarrator-Diagnostics.zip")
                .body(logs.export());
    }

    @PostMapping("/client-events")
    public void clientEvent(@RequestBody ClientEvent event) {
        String level = logs.sanitize(limit(event.level(), 20));
        String message = logs.sanitize(limit(event.message(), 1000));
        String context = logs.sanitize(limit(event.context(), 300));
        log.warn("CLIENT_EVENT level={} context={} message={}", level, context, message);
    }

    private String limit(String value, int maximum) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), maximum));
    }

    public record ClientEvent(String level, String message, String context) { }
}
