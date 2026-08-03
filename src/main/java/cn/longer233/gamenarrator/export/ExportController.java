package cn.longer233.gamenarrator.export;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ExportController {
    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @GetMapping("/export-presets")
    public List<ExportPresetView> presets() {
        return service.presets();
    }

    @GetMapping("/tasks/{taskId}/exports")
    public List<ExportJobView> jobs(@PathVariable UUID taskId) {
        return service.jobs(taskId);
    }

    @PostMapping("/tasks/{taskId}/exports")
    public ResponseEntity<ExportJobView> create(@PathVariable UUID taskId,
                                                @Valid @RequestBody CreateExportRequest request) {
        return ResponseEntity.accepted().body(service.create(taskId, request));
    }

    @GetMapping("/exports/{jobId}")
    public ExportJobView job(@PathVariable UUID jobId) {
        return service.findJob(jobId);
    }

    @GetMapping("/exports/{jobId}/download")
    public ResponseEntity<?> download(@PathVariable UUID jobId) {
        var download = service.download(jobId);
        String encoded = java.net.URLEncoder.encode(download.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.resource().getFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(download.resource());
    }
}
