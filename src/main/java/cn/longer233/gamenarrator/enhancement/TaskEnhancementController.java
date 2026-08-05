package cn.longer233.gamenarrator.enhancement;

import cn.longer233.gamenarrator.effect.EffectSettingsRequest;
import cn.longer233.gamenarrator.script.AutoAssetAssignmentView;
import cn.longer233.gamenarrator.script.StoryboardAssetPlacementService;
import cn.longer233.gamenarrator.task.application.VideoTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/enhancements")
public class TaskEnhancementController {
    private final TaskEnhancementService enhancements;
    private final StoryboardAssetPlacementService assets;
    private final VideoTaskService tasks;

    public TaskEnhancementController(TaskEnhancementService enhancements,
                                     StoryboardAssetPlacementService assets, VideoTaskService tasks) {
        this.enhancements = enhancements;
        this.assets = assets;
        this.tasks = tasks;
    }

    @GetMapping
    public List<EnhancementJobView> status(@PathVariable UUID taskId) {
        return enhancements.status(taskId);
    }

    @PostMapping("/transcription")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EnhancementJobView transcribe(@PathVariable UUID taskId) {
        return enhancements.startTranscription(taskId);
    }

    @PostMapping("/assets")
    public AutoAssetAssignmentView autoAssets(@PathVariable UUID taskId) {
        enhancements.requireIdleTask(taskId);
        return assets.autoAssign(taskId);
    }

    @PostMapping("/effects")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void autoEffects(@PathVariable UUID taskId) {
        enhancements.requireIdleTask(taskId);
        tasks.rerenderEffects(taskId, EffectSettingsRequest.defaults());
    }
}
