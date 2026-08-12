package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.ai.ModelAdapter;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import cn.longer233.gamenarrator.pipeline.StageProgressUpdate;

public interface VisionAnalyzer extends ModelAdapter {
    VideoUnderstandingResult analyze(Path manifestPath, String transcriptText);
    VideoUnderstandingResult analyze(Path manifestPath, String transcriptText, IntConsumer progress);
    default VideoUnderstandingResult analyzeDetailed(Path manifestPath, String transcriptText,
            Consumer<StageProgressUpdate> progress) {
        return analyze(manifestPath, transcriptText, value -> progress.accept(
                StageProgressUpdate.of(value, "FRAME", 0, 0, "分析画面")));
    }
    VideoUnderstandingResult analyzeWithoutAi(Path manifestPath, String transcriptText);
    String model();
}
