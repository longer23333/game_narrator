package cn.longer233.gamenarrator.transcription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sound.sampled.AudioSystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class WhisperCppTranscriber implements Transcriber {

    private static final Logger log = LoggerFactory.getLogger(WhisperCppTranscriber.class);
    private static final String CACHE_FILE = "transcript.cache.json";
    private final Path executable;
    private final Path model;
    private final String language;
    private final int threads;
    private final ObjectMapper objectMapper;
    private final Duration chunkTimeout;
    private final long longAudioThresholdMillis;
    private final long chunkMillis;

    public WhisperCppTranscriber(
            @Value("${game-narrator.whisper.executable}") String executable,
            @Value("${game-narrator.whisper.model}") String model,
            @Value("${game-narrator.whisper.language:auto}") String language,
            @Value("${game-narrator.whisper.threads:8}") int threads,
            @Value("${game-narrator.whisper.long-audio-threshold-minutes:30}") int thresholdMinutes,
            @Value("${game-narrator.whisper.chunk-minutes:20}") int chunkMinutes,
            @Value("${game-narrator.whisper.chunk-timeout-minutes:45}") int chunkTimeoutMinutes,
            ObjectMapper objectMapper
    ) {
        this.executable = Path.of(executable).toAbsolutePath().normalize();
        this.model = Path.of(model).toAbsolutePath().normalize();
        this.language = language;
        this.threads = threads;
        this.objectMapper = objectMapper;
        this.longAudioThresholdMillis = Duration.ofMinutes(Math.max(5, thresholdMinutes)).toMillis();
        this.chunkMillis = Duration.ofMinutes(Math.max(5, chunkMinutes)).toMillis();
        this.chunkTimeout = Duration.ofMinutes(Math.max(5, chunkTimeoutMinutes));
    }

    @Override
    public TranscriptionResult transcribe(Path audioPath) {
        validateRuntime(audioPath);
        Path outputPrefix = audioPath.getParent().resolve("transcript");
        TranscriptionResult cached = readCache(audioPath);
        if (cached != null) {
            log.info("TRANSCRIPTION_CACHE_HIT audio={} characterCount={}", audioPath, cached.text().length());
            return cached;
        }
        log.info("TRANSCRIPTION_BEGIN audio={} model={} language={} threads={}",
                audioPath, model.getFileName(), language, threads);
        long durationMillis = audioDurationMillis(audioPath);
        String output = durationMillis > longAudioThresholdMillis
                ? transcribeChunks(audioPath, outputPrefix, durationMillis)
                : run(command(audioPath, outputPrefix, null), Duration.ofHours(2), "完整音频");
        TranscriptionResult result = readResult(outputPrefix, output);
        writeCache(audioPath, result);
        return result;
    }

    /**
     * 同一份音频 + 同一模型/语言的成功转写结果可安全复用：重试与手动重新增强时
     * 不必再花几分钟跑 Whisper。缓存失效条件写死在元数据里，不做启发式猜测。
     */
    private TranscriptionResult readCache(Path audioPath) {
        Path cachePath = audioPath.getParent().resolve(CACHE_FILE);
        if (!Files.isRegularFile(cachePath)) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(cachePath.toFile());
            if (!audioPath.toAbsolutePath().normalize().toString().equals(node.path("audioPath").asText())) return null;
            if (Files.size(audioPath) != node.path("audioSize").asLong()) return null;
            if (Files.getLastModifiedTime(audioPath).toMillis() != node.path("audioModified").asLong()) return null;
            if (!model.toString().equals(node.path("modelPath").asText())) return null;
            if (Files.size(model) != node.path("modelSize").asLong()) return null;
            if (Files.getLastModifiedTime(model).toMillis() != node.path("modelModified").asLong()) return null;
            if (!language.equals(node.path("language").asText())) return null;
            Path textPath = Path.of(node.path("textPath").asText());
            if (!Files.isRegularFile(textPath)) return null;
            String subtitleValue = node.path("subtitlePath").asText("");
            String jsonValue = node.path("jsonPath").asText("");
            String subtitlePath = !subtitleValue.isEmpty() && Files.isRegularFile(Path.of(subtitleValue))
                    ? subtitleValue : null;
            String jsonPath = !jsonValue.isEmpty() && Files.isRegularFile(Path.of(jsonValue)) ? jsonValue : null;
            String text = Files.readString(textPath, StandardCharsets.UTF_8).trim();
            return new TranscriptionResult(text, textPath.toString(), subtitlePath, jsonPath);
        } catch (Exception exception) {
            log.debug("TRANSCRIPTION_CACHE_IGNORED audio={} reason={}", audioPath, exception.getMessage());
            return null;
        }
    }

    private void writeCache(Path audioPath, TranscriptionResult result) {
        if (result == null || result.textPath() == null) return;
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("audioPath", audioPath.toAbsolutePath().normalize().toString());
            payload.put("audioSize", Files.size(audioPath));
            payload.put("audioModified", Files.getLastModifiedTime(audioPath).toMillis());
            payload.put("modelPath", model.toString());
            payload.put("modelSize", Files.size(model));
            payload.put("modelModified", Files.getLastModifiedTime(model).toMillis());
            payload.put("language", language);
            payload.put("threads", threads);
            payload.put("textPath", result.textPath());
            payload.put("subtitlePath", result.subtitlePath() == null ? "" : result.subtitlePath());
            payload.put("jsonPath", result.detailJsonPath() == null ? "" : result.detailJsonPath());
            payload.put("createdAt", java.time.OffsetDateTime.now().toString());
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper,
                    audioPath.getParent().resolve(CACHE_FILE), payload);
        } catch (Exception exception) {
            // 缓存写不进去只损失一次优化机会，不能让成功完成的转写变成失败。
            log.warn("TRANSCRIPTION_CACHE_WRITE_FAILED audio={} reason={}", audioPath, exception.getMessage());
        }
    }


    private List<String> command(Path audioPath, Path outputPrefix, WhisperChunkSupport.Chunk chunk) {
        List<String> command = new java.util.ArrayList<>(List.of(
                executable.toString(),
                "-m", model.toString(),
                "-f", audioPath.toString(),
                "-l", language,
                "-t", String.valueOf(threads),
                "-otxt", "-osrt", "-oj",
                "-of", outputPrefix.toString()));
        if (chunk != null) command.addAll(List.of("-ot", String.valueOf(chunk.offsetMillis()),
                "-d", String.valueOf(chunk.durationMillis())));
        return command;
    }

    private String transcribeChunks(Path audioPath, Path outputPrefix, long durationMillis) {
        List<Path> prefixes = new java.util.ArrayList<>();
        StringBuilder logs = new StringBuilder();
        List<WhisperChunkSupport.Chunk> chunks = WhisperChunkSupport.plan(durationMillis, chunkMillis);
        log.info("TRANSCRIPTION_CHUNKING durationMillis={} chunkCount={} chunkMillis={}",
                durationMillis, chunks.size(), chunkMillis);
        try {
            for (WhisperChunkSupport.Chunk chunk : chunks) {
                Path prefix = audioPath.getParent().resolve("transcript-part-%03d".formatted(chunk.index()));
                prefixes.add(prefix);
                logs.append(run(command(audioPath, prefix, chunk), chunkTimeout,
                        "第 " + chunk.index() + "/" + chunks.size() + " 段")).append('\n');
            }
            WhisperChunkSupport.merge(objectMapper, prefixes, outputPrefix);
            return logs.toString();
        } catch (Exception failure) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("无法合并 Whisper 分段结果：" + failure.getMessage(), failure);
        } finally {
            for (Path prefix : prefixes) for (String extension : List.of(".txt", ".srt", ".json")) {
                try { Files.deleteIfExists(Path.of(prefix + extension)); }
                catch (IOException cleanupFailure) { log.debug("TRANSCRIPTION_CHUNK_CLEANUP_FAILED path={} reason={}", prefix, cleanupFailure.getMessage()); }
            }
        }
    }

    private TranscriptionResult readResult(Path outputPrefix, String output) {
        Path textPath = Path.of(outputPrefix + ".txt");
        Path subtitlePath = Path.of(outputPrefix + ".srt");
        Path jsonPath = Path.of(outputPrefix + ".json");
        if (!Files.isRegularFile(textPath)) {
            throw new IllegalStateException("Whisper 未生成文本文件：" + tail(output, 1200));
        }
        try {
            String text = Files.readString(textPath, StandardCharsets.UTF_8).trim();
            log.info("TRANSCRIPTION_SUCCESS characterCount={} text={} subtitle={} json={}",
                    text.length(), textPath, subtitlePath, jsonPath);
            return new TranscriptionResult(
                    text,
                    textPath.toString(),
                    Files.isRegularFile(subtitlePath) ? subtitlePath.toString() : null,
                    Files.isRegularFile(jsonPath) ? jsonPath.toString() : null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Whisper 转写结果：" + exception.getMessage(), exception);
        }
    }

    private long audioDurationMillis(Path audioPath) {
        try (var input = AudioSystem.getAudioInputStream(audioPath.toFile())) {
            if (input.getFrameLength() <= 0 || input.getFormat().getFrameRate() <= 0) return 0;
            return Math.round(input.getFrameLength() / input.getFormat().getFrameRate() * 1000d);
        } catch (Exception failure) {
            log.debug("TRANSCRIPTION_DURATION_UNKNOWN audio={} reason={}", audioPath, failure.getMessage());
            return 0;
        }
    }

    public boolean runtimeAvailable() {
        return Files.isRegularFile(executable) && Files.isRegularFile(model);
    }

    @Override
    public String engineId() {
        return "whisper-cpp";
    }

    @Override
    public boolean available() {
        return runtimeAvailable();
    }

    @Override
    public Map<String, Object> diagnostics() {
        return Map.of("engine", engineId(), "available", available(),
                "executable", executable.toString(), "model", model.toString());
    }

    public Path executable() {
        return executable;
    }

    public Path model() {
        return model;
    }

    private void validateRuntime(Path audioPath) {
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException("未找到 whisper.cpp 执行程序：" + executable
                    + "。请在项目根目录运行 .\\scripts\\setup-whisper.ps1，或提供平台字幕");
        }
        if (!Files.isRegularFile(model)) {
            throw new IllegalStateException("未找到 Whisper 模型：" + model
                    + "。请在项目根目录运行 .\\scripts\\setup-whisper.ps1");
        }
        if (!Files.isRegularFile(audioPath)) {
            throw new IllegalStateException("未找到待转写音频：" + audioPath);
        }
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException("未找到 whisper.cpp 执行程序：" + executable);
        }
        if (!Files.isRegularFile(model)) {
            throw new IllegalStateException("未找到 Whisper 模型：" + model);
        }
        if (!Files.isRegularFile(audioPath)) {
            throw new IllegalStateException("未找到待转写音频：" + audioPath);
        }
    }

    private String run(List<String> command, Duration timeout, String scope) {
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(
                    command, timeout, null,
                    line -> { if (line.contains("progress")) log.debug("TRANSCRIPTION_PROGRESS {}", line.trim()); });
            if (result.exitCode() != 0) throw new IllegalStateException("Whisper 转写失败，退出码 "
                    + result.exitCode() + "：" + tail(result.output(), 2000));
            return result.output();
        } catch (cn.longer233.gamenarrator.common.ExternalProcessRunner.ProcessTimeoutException exception) {
            throw new IllegalStateException("Whisper " + scope + "转写超过 " + timeout.toMinutes()
                    + " 分钟；可降低 WHISPER_CHUNK_MINUTES 或检查设备性能", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 whisper.cpp：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Whisper 转写被中断", exception);
        }
    }

    private String tail(CharSequence value, int limit) {
        int start = Math.max(0, value.length() - limit);
        return value.subSequence(start, value.length()).toString();
    }
}
