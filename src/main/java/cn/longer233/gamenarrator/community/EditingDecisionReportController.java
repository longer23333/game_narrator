package cn.longer233.gamenarrator.community;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/decision-report")
public class EditingDecisionReportController {
    private final EditingDecisionReportService service;
    public EditingDecisionReportController(EditingDecisionReportService service) { this.service = service; }
    @GetMapping public EditingDecisionReportView latest(@PathVariable UUID taskId) {
        EditingDecisionReportView report = service.latest(taskId);
        return report == null ? service.generate(taskId) : report;
    }
    @PostMapping public EditingDecisionReportView generate(@PathVariable UUID taskId) { return service.generate(taskId); }
    @GetMapping(value="/export", produces="text/markdown;charset=UTF-8")
    public ResponseEntity<byte[]> export(@PathVariable UUID taskId) {
        EditingDecisionReportView report = service.latest(taskId);
        if (report == null) report = service.generate(taskId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("editing-decision-report-" + taskId + ".md").build().toString())
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(report.markdown().getBytes(StandardCharsets.UTF_8));
    }
}
