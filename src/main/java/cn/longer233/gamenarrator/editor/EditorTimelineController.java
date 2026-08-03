package cn.longer233.gamenarrator.editor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/editor")
public class EditorTimelineController {
    private final EditorTimelineService service;
    public EditorTimelineController(EditorTimelineService service) { this.service = service; }
    @GetMapping public JsonNode timeline(@PathVariable UUID taskId) { return service.timeline(taskId); }
    @PostMapping("/commands") public JsonNode command(@PathVariable UUID taskId, @Valid @RequestBody EditorCommandRequest request) { return service.command(taskId, request); }
    @GetMapping("/waveform") public Map<String,Object> waveform(@PathVariable UUID taskId, @RequestParam(defaultValue="800") int points) { return service.waveform(taskId, points); }
}
