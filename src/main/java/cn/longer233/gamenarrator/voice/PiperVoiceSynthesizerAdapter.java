package cn.longer233.gamenarrator.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import cn.longer233.gamenarrator.pipeline.StageProgressUpdate;

@Component
@ConditionalOnProperty(name = "game-narrator.ai.tts-engine", havingValue = "piper", matchIfMissing = true)
public class PiperVoiceSynthesizerAdapter implements VoiceSynthesizer {
    private final PiperVoiceGenerator delegate;
    public PiperVoiceSynthesizerAdapter(PiperVoiceGenerator delegate) { this.delegate = delegate; }
    @Override public String engineId() { return "piper"; }
    @Override public boolean available() { return delegate.available(); }
    @Override public VoiceGenerationResult generate(Path path) { return delegate.generate(path); }
    @Override public VoiceGenerationResult generate(Path path, IntConsumer progress) { return delegate.generate(path, progress); }
    @Override public VoiceGenerationResult generateDetailed(Path path, Consumer<StageProgressUpdate> progress) {
        return delegate.generateDetailed(path, progress);
    }
    @Override public VoiceSegment regenerateSegment(Path path, int index) { return delegate.regenerateSegment(path, index); }
    @Override public VoiceSegment regenerateSegment(Path path, int index, String voiceId, double speed) {
        return delegate.regenerateSegment(path, index, voiceId, speed);
    }
    @Override public VoiceSegment regenerateSegment(Path path, int index, VoiceRegenerationRequest request) {
        return delegate.regenerateSegment(path, index, request);
    }
    @Override public List<VoiceOption> options() { return delegate.options(); }
    @Override public List<VoiceProfile> profiles() { return delegate.profiles(); }
    @Override public Path preview(VoiceRegenerationRequest request, String text) { return delegate.preview(request, text); }
}
