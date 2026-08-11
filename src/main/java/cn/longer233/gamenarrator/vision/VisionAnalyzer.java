package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.ai.ModelAdapter;
import java.nio.file.Path;
import java.util.function.IntConsumer;

public interface VisionAnalyzer extends ModelAdapter {
    VideoUnderstandingResult analyze(Path manifestPath, String transcriptText);
    VideoUnderstandingResult analyze(Path manifestPath, String transcriptText, IntConsumer progress);
    VideoUnderstandingResult analyzeWithoutAi(Path manifestPath, String transcriptText);
    String model();
}
