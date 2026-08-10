package cn.longer233.gamenarrator.event;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/events")
public class GameEventTimelineController {
    private final GameEventTimelineService service;
    private final ConfirmedEventScriptService scripts;

    public GameEventTimelineController(GameEventTimelineService service, ConfirmedEventScriptService scripts) {
        this.service = service;
        this.scripts = scripts;
    }

    @GetMapping
    public List<GameEventView> list(@PathVariable UUID taskId) {
        return service.list(taskId);
    }

    @GetMapping("/knowledge-pack")
    public BossBattleKnowledgePack knowledgePack() {
        return service.knowledgePack();
    }

    @PutMapping("/{eventId}")
    public GameEventView update(@PathVariable UUID taskId, @PathVariable UUID eventId,
                                @Valid @RequestBody UpdateGameEventRequest request) {
        return service.update(taskId, eventId, request);
    }

    @PostMapping("/regenerate-script")
    public cn.longer233.gamenarrator.script.ScriptDocumentView regenerateScript(@PathVariable UUID taskId) {
        return scripts.regenerate(taskId);
    }
}
