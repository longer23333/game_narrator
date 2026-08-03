package cn.longer233.gamenarrator.highlight;

import java.util.List;

public record HighlightSelectionResult(String summary, String manifestPath, List<HighlightClip> clips) {
}
