package cn.longer233.gamenarrator.voice;

import cn.longer233.gamenarrator.ai.ModelAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

public interface VoiceSynthesizer extends ModelAdapter {
    VoiceGenerationResult generate(Path scriptPath);
    default VoiceGenerationResult generate(Path scriptPath, IntConsumer progress) { return generate(scriptPath); }
    VoiceSegment regenerateSegment(Path scriptPath, int clipIndex);
    VoiceSegment regenerateSegment(Path scriptPath, int clipIndex, String voiceId, double speed);
    List<VoiceOption> options();
}
