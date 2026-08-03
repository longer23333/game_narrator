package cn.longer233.gamenarrator.transcription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
public class WhisperCppTranscriber {

    private static final Logger log = LoggerFactory.getLogger(WhisperCppTranscriber.class);
    private final Path executable;
    private final Path model;
    private final String language;
    private final int threads;

    public WhisperCppTranscriber(
            @Value("${game-narrator.whisper.executable}") String executable,
            @Value("${game-narrator.whisper.model}") String model,
            @Value("${game-narrator.whisper.language:auto}") String language,
            @Value("${game-narrator.whisper.threads:8}") int threads
    ) {
        this.executable = Path.of(executable).toAbsolutePath().normalize();
        this.model = Path.of(model).toAbsolutePath().normalize();
        this.language = language;
        this.threads = threads;
    }

    public TranscriptionResult transcribe(Path audioPath) {
        validateRuntime(audioPath);
        Path outputPrefix = audioPath.getParent().resolve("transcript");
        log.info("TRANSCRIPTION_BEGIN audio={} model={} language={} threads={}",
                audioPath, model.getFileName(), language, threads);
        String output = run(List.of(
                executable.toString(),
                "-m", model.toString(),
                "-f", audioPath.toString(),
                "-l", language,
                "-t", String.valueOf(threads),
                "-otxt", "-osrt", "-oj",
                "-of", outputPrefix.toString()
        ));
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

    public boolean runtimeAvailable() {
        return Files.isRegularFile(executable) && Files.isRegularFile(model);
    }

    public Path executable() {
        return executable;
    }

    public Path model() {
        return model;
    }

    private void validateRuntime(Path audioPath) {
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

    private String run(List<String> command) {
        try {
            var result = cn.longer233.gamenarrator.common.ExternalProcessRunner.run(
                    command, Duration.ofHours(2), null,
                    line -> { if (line.contains("progress")) log.debug("TRANSCRIPTION_PROGRESS {}", line.trim()); });
            if (result.exitCode() != 0) throw new IllegalStateException("Whisper 转写失败，退出码 "
                    + result.exitCode() + "：" + tail(result.output(), 2000));
            return result.output();
        } catch (cn.longer233.gamenarrator.common.ExternalProcessRunner.ProcessTimeoutException exception) {
            throw new IllegalStateException("Whisper 转写超过 2 小时", exception);
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
