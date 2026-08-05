package cn.longer233.gamenarrator.editor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/editor/revisions")
public class ProjectRevisionController {
    private final ProjectRevisionService service;
    public ProjectRevisionController(ProjectRevisionService service) { this.service = service; }
    @GetMapping public List<ProjectRevisionView> list(@PathVariable UUID taskId) { return service.list(taskId); }
    @PatchMapping("/{revisionId}") public ProjectRevisionView rename(@PathVariable UUID taskId,
            @PathVariable UUID revisionId, @Valid @RequestBody RenameRevisionRequest request) {
        return service.rename(taskId, revisionId, request);
    }
    @PostMapping("/{revisionId}/checkout") public JsonNode checkout(@PathVariable UUID taskId,
            @PathVariable UUID revisionId) { return service.checkout(taskId, revisionId); }
}
