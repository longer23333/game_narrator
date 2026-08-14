package cn.longer233.gamenarrator.effect;

import cn.longer233.gamenarrator.pipeline.TaskWorkflowStateService;
import cn.longer233.gamenarrator.render.FfmpegVideoRenderer;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;

@Component
public class EffectRerenderWorker {
    private static final Logger log = LoggerFactory.getLogger(EffectRerenderWorker.class);
    private final TaskWorkflowStateService state;
    private final FfmpegVideoRenderer renderer;
    private final EffectPresetCatalog presetCatalog;

    public EffectRerenderWorker(TaskWorkflowStateService state, FfmpegVideoRenderer renderer,
                                EffectPresetCatalog presetCatalog) {
        this.state = state;
        this.renderer = renderer;
        this.presetCatalog = presetCatalog;
    }

    @Async
    public void rerender(UUID taskId, EffectSettingsRequest settings) {
        try {
            var context = state.context(taskId);
            if (context.timelinePath() == null) throw new IllegalStateException("任务尚未生成时间线");
            state.markRenderingRunning(taskId);
            EffectPreset preset = presetCatalog.require(settings.presetCode());
            if (settings.intensity() != null) {
                preset = new EffectPreset(preset.code(), preset.name(), preset.description(),
                        settings.intensity(), preset.maxEffectsPerClip(),
                        preset.transitionDurationSeconds(), preset.subtitleTheme(),
                        preset.sourceAudioVolume(), preset.preferredEffects(), preset.allowedTransitions());
            }
            var result = renderer.render(Path.of(context.sourceVideoPath()),
                    Path.of(context.timelinePath()), context.hasAudio(), preset, settings);
            state.markRenderingCompleted(taskId, result);
            log.info("EFFECT_RERENDER_SUCCESS taskId={} output={}", taskId, result.videoPath());
        } catch (Exception exception) {
            state.markFailed(taskId, ProcessingStageType.RENDERING, exception.getMessage());
            log.error("EFFECT_RERENDER_FAILED taskId={}", taskId, exception);
        }
    }
}
