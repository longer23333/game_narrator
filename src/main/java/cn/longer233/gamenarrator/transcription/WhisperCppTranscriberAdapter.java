package cn.longer233.gamenarrator.transcription;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "game-narrator.ai.asr-engine", havingValue = "whisper-cpp", matchIfMissing = true)
public class WhisperCppTranscriberAdapter implements Transcriber {
    private final WhisperCppTranscriber delegate;
    public WhisperCppTranscriberAdapter(WhisperCppTranscriber delegate) { this.delegate = delegate; }
    @Override public String engineId() { return "whisper-cpp"; }
    @Override public boolean available() { return delegate.runtimeAvailable(); }
    @Override public TranscriptionResult transcribe(Path audioPath) { return delegate.transcribe(audioPath); }
    @Override public Map<String, Object> diagnostics() { return Map.of(
            "engine", engineId(), "available", available(), "executable", delegate.executable().toString(),
            "model", delegate.model().toString()); }
}
