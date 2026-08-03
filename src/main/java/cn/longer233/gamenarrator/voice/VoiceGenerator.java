package cn.longer233.gamenarrator.voice;

import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

public interface VoiceGenerator {
    String engineId();
    boolean available();
    VoiceGenerationResult generate(Path scriptPath);
    default VoiceGenerationResult generate(Path scriptPath, IntConsumer progress) {
        return generate(scriptPath);
    }
    VoiceSegment regenerateSegment(Path scriptPath, int clipIndex);
    VoiceSegment regenerateSegment(Path scriptPath, int clipIndex, String voiceId, double speed);
    List<VoiceOption> options();
}
