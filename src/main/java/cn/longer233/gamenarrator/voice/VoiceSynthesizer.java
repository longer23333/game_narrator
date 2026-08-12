package cn.longer233.gamenarrator.voice;

import cn.longer233.gamenarrator.ai.ModelAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import cn.longer233.gamenarrator.pipeline.StageProgressUpdate;

public interface VoiceSynthesizer extends ModelAdapter {
    VoiceGenerationResult generate(Path scriptPath);
    default VoiceGenerationResult generate(Path scriptPath, IntConsumer progress) { return generate(scriptPath); }
    default VoiceGenerationResult generateDetailed(Path scriptPath, Consumer<StageProgressUpdate> progress) {
        return generate(scriptPath, value -> progress.accept(StageProgressUpdate.of(value, "MODEL", 0, 0, "生成配音")));
    }
    VoiceSegment regenerateSegment(Path scriptPath, int clipIndex);
    VoiceSegment regenerateSegment(Path scriptPath, int clipIndex, String voiceId, double speed);
    default VoiceSegment regenerateSegment(Path scriptPath, int clipIndex, VoiceRegenerationRequest request) {
        return regenerateSegment(scriptPath, clipIndex, request == null ? null : request.voiceId(),
                request == null ? 1.0 : request.effectiveSpeed());
    }
    List<VoiceOption> options();
    default List<VoiceProfile> profiles() { return List.of(); }
    default Path preview(VoiceRegenerationRequest request, String text) {
        throw new UnsupportedOperationException("Voice preview is not supported");
    }
}
