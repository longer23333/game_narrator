package cn.longer233.gamenarrator.quality;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/quality")
public class NarrativeConsistencyController {
    private final NarrativeConsistencyService service;
    public NarrativeConsistencyController(NarrativeConsistencyService service) { this.service = service; }
    @GetMapping("/narrative-consistency")
    public NarrativeQualityReport inspect(@PathVariable UUID taskId) { return service.inspect(taskId); }
}
