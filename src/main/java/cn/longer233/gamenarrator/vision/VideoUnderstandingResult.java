package cn.longer233.gamenarrator.vision;

import java.util.List;

public record VideoUnderstandingResult(
        String summary,
        String analysisPath,
        List<FrameUnderstanding> frames
) {
}
