package cn.longer233.gamenarrator.vision;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import cn.longer233.gamenarrator.pipeline.StageProgressUpdate;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "game-narrator.ai.vlm-engine", havingValue = "ollama", matchIfMissing = true)
public class OllamaVisionAnalyzerAdapter implements VisionAnalyzer {
    private final OllamaVisionClient delegate;
    public OllamaVisionAnalyzerAdapter(OllamaVisionClient delegate) { this.delegate = delegate; }
    @Override public String engineId() { return "ollama"; }
    @Override public boolean available() { return delegate.available(); }
    @Override public String model() { return delegate.model(); }
    @Override public Map<String, Object> diagnostics() { return Map.of(
            "engine", engineId(), "available", available(), "url", delegate.baseUri().toString(), "model", model()); }
    @Override public VideoUnderstandingResult analyze(Path path, String text) { return delegate.analyze(path, text); }
    @Override public VideoUnderstandingResult analyze(Path path, String text, IntConsumer progress) {
        return delegate.analyze(path, text, progress);
    }
    @Override public VideoUnderstandingResult analyzeDetailed(Path path, String text,
            Consumer<StageProgressUpdate> progress) { return delegate.analyzeDetailed(path, text, progress); }
    @Override public VideoUnderstandingResult analyzeWithoutAi(Path path, String text) {
        return delegate.analyzeWithoutAi(path, text);
    }
}
