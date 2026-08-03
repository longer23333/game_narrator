package cn.longer233.gamenarrator.vision;

import java.util.List;

public record VideoContentAnalysis(
        String overview,
        List<String> topics,
        String tone,
        List<String> keyEvents,
        List<HighlightHint> highlightHints,
        String highlightStrategy
) {
}
