package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.event.GameEventFact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "game-narrator.ai.llm-engine", havingValue = "adaptive-chat", matchIfMissing = true)
public class AdaptiveTextGeneratorAdapter implements TextGenerator {
    private final OllamaScriptGenerator delegate;
    public AdaptiveTextGeneratorAdapter(OllamaScriptGenerator delegate) { this.delegate = delegate; }
    @Override public String engineId() { return "adaptive-chat"; }
    @Override public boolean available() { return true; }
    @Override public GeneratedScript generate(Path path, String category, String style, String brief,
                                               String transcript, List<GameEventFact> facts) {
        return delegate.generate(path, category, style, brief, transcript, facts);
    }
    @Override public GeneratedScript generateWithoutAi(Path path) { return delegate.generateWithoutAi(path); }
    @Override public ScriptSegment regenerateSegment(ScriptSegment current, String instruction,
            String previous, String next) { return delegate.regenerateSegment(current, instruction, previous, next); }
    @Override public ScriptQualityReview reviewQuality(ScriptDocumentView document, Map<String, Object> reviews) {
        return delegate.reviewQuality(document, reviews);
    }
}
