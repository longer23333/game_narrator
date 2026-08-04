package cn.longer233.gamenarrator.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import cn.longer233.gamenarrator.transcription.WhisperCppTranscriber;
import cn.longer233.gamenarrator.vision.OllamaVisionClient;
import cn.longer233.gamenarrator.importer.YtDlpMediaImporter;
import cn.longer233.gamenarrator.asset.BgeAssetSemanticSearch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import cn.longer233.gamenarrator.task.domain.ProcessingStageType;
import cn.longer233.gamenarrator.task.domain.VideoTask;

@Service
public class SystemDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(SystemDiagnosticsService.class);

    private final Path storageRoot;
    private final String ffmpegCommand;
    private final WhisperCppTranscriber transcriber;
    private final OllamaVisionClient visionClient;
    private final YtDlpMediaImporter mediaImporter;
    private final BgeAssetSemanticSearch semanticSearch;

    public SystemDiagnosticsService(
            @Value("${game-narrator.storage-root}") String storageRoot,
            @Value("${game-narrator.ffmpeg-command}") String ffmpegCommand,
            WhisperCppTranscriber transcriber,
            OllamaVisionClient visionClient,
            YtDlpMediaImporter mediaImporter,
            BgeAssetSemanticSearch semanticSearch
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.ffmpegCommand = ffmpegCommand;
        this.transcriber = transcriber;
        this.visionClient = visionClient;
        this.mediaImporter = mediaImporter;
        this.semanticSearch = semanticSearch;
    }

    public Map<String, Object> inspect() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("javaVersion", Runtime.version().toString());
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.put("maxMemoryMb", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        report.put("externalProcessesActive",
                cn.longer233.gamenarrator.common.ExternalProcessRunner.activeCounts());
        report.put("storagePath", storageRoot.toString());
        report.put("storageWritable", storageWritable());
        report.put("ffmpegCommand", ffmpegCommand);
        report.put("ffmpegAvailable", commandAvailable(ffmpegCommand, "-version"));
        report.put("whisperExecutable", transcriber.executable().toString());
        report.put("whisperModel", transcriber.model().toString());
        report.put("whisperAvailable", transcriber.runtimeAvailable());
        report.put("ollamaUrl", visionClient.baseUri().toString());
        report.put("visionModel", visionClient.model());
        report.put("visionModelAvailable", visionClient.available());
        report.put("semanticModel", semanticSearch.model());
        report.put("semanticModelAvailable", semanticSearch.available());
        report.put("mediaImporterExecutable", mediaImporter.executable().toString());
        report.put("mediaImporterAvailable", mediaImporter.available());
        return report;
    }

    public void logStartupReport() {
        Map<String, Object> report = inspect();
        log.info("SYSTEM_DIAGNOSTICS {}", report);
        if (!Boolean.TRUE.equals(report.get("storageWritable"))) {
            log.error("SYSTEM_REQUIREMENT_FAILED component=storage path={}", storageRoot);
        }
        if (!Boolean.TRUE.equals(report.get("ffmpegAvailable"))) {
            log.warn("SYSTEM_REQUIREMENT_MISSING component=ffmpeg command={} "
                    + "impact=video_processing_unavailable", ffmpegCommand);
        }
        if (!Boolean.TRUE.equals(report.get("whisperAvailable"))) {
            log.warn("SYSTEM_REQUIREMENT_MISSING component=whisper executable={} model={} "
                            + "impact=transcription_unavailable",
                    transcriber.executable(), transcriber.model());
        }
        if (!Boolean.TRUE.equals(report.get("visionModelAvailable"))) {
            log.warn("SYSTEM_REQUIREMENT_MISSING component=vision_model url={} model={} "
                            + "impact=video_understanding_unavailable",
                    visionClient.baseUri(), visionClient.model());
        }
        if (!Boolean.TRUE.equals(report.get("mediaImporterAvailable"))) {
            log.warn("SYSTEM_REQUIREMENT_MISSING component=media_importer executable={} "
                    + "impact=platform_media_import_unavailable", mediaImporter.executable());
        }
    }

    public List<String> recoveryBlockers(VideoTask task) {
        List<String> blockers = new ArrayList<>();
        boolean needsFfmpeg = !task.isStageCompleted(ProcessingStageType.SCENE_DETECTION)
                || !task.isStageCompleted(ProcessingStageType.RENDERING);
        if (needsFfmpeg && !commandAvailable(ffmpegCommand, "-version")) blockers.add("ffmpeg");
        boolean needsWhisper = task.isAutomaticGenerationEnabled()
                && task.getExtractedAudioPath() != null
                && !task.isStageCompleted(ProcessingStageType.TRANSCRIPTION);
        if (needsWhisper && !transcriber.runtimeAvailable()) blockers.add("whisper");
        boolean needsVision = task.isAutomaticGenerationEnabled() && task.isCloudVisionEnabled()
                && !task.isStageCompleted(ProcessingStageType.VIDEO_UNDERSTANDING);
        if (needsVision && !visionClient.available()) blockers.add("vision-model");
        return List.copyOf(blockers);
    }

    private boolean storageWritable() {
        try {
            Files.createDirectories(storageRoot);
            return Files.isWritable(storageRoot);
        } catch (Exception exception) {
            log.error("STORAGE_CHECK_FAILED path={} message={}",
                    storageRoot, exception.getMessage(), exception);
            return false;
        }
    }

    private boolean commandAvailable(String... command) {
        try {
            return cn.longer233.gamenarrator.common.ExternalProcessRunner.run(
                    java.util.List.of(command), Duration.ofSeconds(3)).exitCode() == 0;
        } catch (Exception exception) {
            log.debug("COMMAND_CHECK_FAILED command={} message={}",
                    String.join(" ", command), exception.getMessage());
            return false;
        }
    }
}
