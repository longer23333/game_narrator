package cn.longer233.gamenarrator.enhancement;

import cn.longer233.gamenarrator.pipeline.TaskWorkflowStateService;
import cn.longer233.gamenarrator.transcription.PlatformSubtitleReader;
import cn.longer233.gamenarrator.transcription.SubtitleChunkAnalysisService;
import cn.longer233.gamenarrator.transcription.TranscriptionResult;
import cn.longer233.gamenarrator.transcription.WhisperCppTranscriber;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class TaskEnhancementService {
    private static final Logger log = LoggerFactory.getLogger(TaskEnhancementService.class);
    private final TaskWorkflowStateService state;
    private final PlatformSubtitleReader platformSubtitles;
    private final WhisperCppTranscriber transcriber;
    private final SubtitleChunkAnalysisService subtitleAnalysis;
    private final Executor executor;
    private final VideoTaskRepository repository;
    private final ConcurrentHashMap<UUID, EnhancementJobView> transcriptionJobs = new ConcurrentHashMap<>();

    public TaskEnhancementService(TaskWorkflowStateService state, PlatformSubtitleReader platformSubtitles,
                                  WhisperCppTranscriber transcriber,
                                  SubtitleChunkAnalysisService subtitleAnalysis,
                                  VideoTaskRepository repository,
                                  @Qualifier("taskExecutor") Executor executor) {
        this.state = state;
        this.platformSubtitles = platformSubtitles;
        this.transcriber = transcriber;
        this.subtitleAnalysis = subtitleAnalysis;
        this.repository = repository;
        this.executor = executor;
    }

    public List<EnhancementJobView> status(UUID taskId) {
        var context = state.context(taskId);
        boolean idle = repository.findById(taskId).map(task -> task.getStatus() != TaskStatus.PROCESSING).orElse(false);
        EnhancementJobView transcription = transcriptionJobs.get(taskId);
        if (transcription == null) {
            boolean available = idle && context.sceneDetectionCompleted() && context.hasAudio();
            transcription = new EnhancementJobView("TRANSCRIPTION", "IDLE",
                    available ? "可重新识别语音并生成字幕" : "需要平台字幕或已安装的 Whisper", available);
        }
        return List.of(transcription,
                new EnhancementJobView("AUTO_ASSETS", "IDLE",
                        context.scriptGenerationCompleted() ? "可按分镜自动匹配素材" : "需要先建立分镜",
                        idle && context.scriptGenerationCompleted()),
                new EnhancementJobView("AUTO_EFFECTS", "IDLE",
                        context.timelinePlanningCompleted() ? "可自动规划特效并重新渲染" : "需要先建立时间线",
                        idle && context.timelinePlanningCompleted()));
    }

    public EnhancementJobView startTranscription(UUID taskId) {
        requireIdleTask(taskId);
        var context = state.context(taskId);
        if (!context.sceneDetectionCompleted() || !context.hasAudio() || context.extractedAudioPath() == null) {
            throw new IllegalStateException("任务尚未完成音频准备，不能执行语音转写");
        }
        EnhancementJobView running = new EnhancementJobView("TRANSCRIPTION", "RUNNING", "正在识别语音并生成字幕", true);
        EnhancementJobView existing = transcriptionJobs.putIfAbsent(taskId, running);
        if (existing != null && "RUNNING".equals(existing.status())) throw new IllegalStateException("语音转写正在运行");
        transcriptionJobs.put(taskId, running);
        executor.execute(() -> transcribe(taskId));
        return running;
    }

    public void requireIdleTask(UUID taskId) {
        var task = repository.findById(taskId).orElseThrow(() -> new IllegalArgumentException("剪辑任务不存在"));
        if (task.getStatus() == TaskStatus.PROCESSING) {
            throw new IllegalStateException("主流水线正在处理，请完成或取消后再运行增强工具");
        }
    }

    private void transcribe(UUID taskId) {
        try {
            var context = state.context(taskId);
            TranscriptionResult result = platformSubtitles.read(Path.of(context.sourceVideoPath()));
            if (result == null) {
                if (!transcriber.runtimeAvailable()) throw new IllegalStateException("Whisper 尚未安装或不可用");
                result = transcriber.transcribe(Path.of(context.extractedAudioPath()));
            }
            state.applyTranscriptionEnhancement(taskId, result);
            if (result.subtitlePath() != null) subtitleAnalysis.analyze(Path.of(result.subtitlePath()));
            EnhancementJobView completed = new EnhancementJobView("TRANSCRIPTION", "COMPLETED",
                    "语音转写和字幕已更新，可继续手动编辑", true);
            transcriptionJobs.put(taskId, completed);
            log.info("TASK_ENHANCEMENT_COMPLETED taskId={} type=TRANSCRIPTION characters={}", taskId, result.text().length());
        } catch (Exception exception) {
            transcriptionJobs.put(taskId, new EnhancementJobView("TRANSCRIPTION", "FAILED",
                    concise(exception), true));
            log.warn("TASK_ENHANCEMENT_FAILED taskId={} type=TRANSCRIPTION reason={}", taskId, concise(exception));
        }
    }

    private String concise(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() <= 300 ? message : message.substring(0, 297) + "...";
    }
}
