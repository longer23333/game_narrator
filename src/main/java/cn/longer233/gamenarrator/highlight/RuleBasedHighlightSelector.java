package cn.longer233.gamenarrator.highlight;

import cn.longer233.gamenarrator.vision.FrameUnderstanding;
import cn.longer233.gamenarrator.vision.HighlightHint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuleBasedHighlightSelector {
    private static final Logger log = LoggerFactory.getLogger(RuleBasedHighlightSelector.class);
    private static final double STORY_SEGMENT_SECONDS = 30.0;
    private final ObjectMapper objectMapper;

    public RuleBasedHighlightSelector(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public HighlightSelectionResult select(Path visualAnalysisPath, double videoDurationSeconds,
                                           int targetDurationSeconds) {
        return select(visualAnalysisPath, videoDurationSeconds, targetDurationSeconds, "FULL_VIDEO");
    }

    public HighlightSelectionResult select(Path visualAnalysisPath, double videoDurationSeconds,
                                           int targetDurationSeconds, String editingScope) {
        try {
            JsonNode document = objectMapper.readTree(visualAnalysisPath.toFile());
            List<FrameUnderstanding> frames = objectMapper.readerForListOf(FrameUnderstanding.class)
                    .readValue(document.path("frames"));
            if (frames.isEmpty()) throw new IllegalStateException("视觉分析结果中没有候选镜头");
            List<HighlightHint> hints = document.path("contentAnalysis").path("highlightHints").isArray()
                    ? objectMapper.readerForListOf(HighlightHint.class)
                            .readValue(document.path("contentAnalysis").path("highlightHints"))
                    : List.of();
            boolean highlightsOnly = "HIGHLIGHTS".equalsIgnoreCase(editingScope);
            List<HighlightClip> clips = highlightsOnly
                    ? buildHighlightClips(frames, videoDurationSeconds, targetDurationSeconds, hints)
                    : buildContinuousStoryClips(frames, videoDurationSeconds, hints);
            double totalSeconds = clips.stream().mapToDouble(HighlightClip::durationSeconds).sum();
            String summary = highlightsOnly
                    ? "已按精彩片段模式选出 %d 个片段，共 %.1f 秒".formatted(clips.size(), totalSeconds)
                    : "已将完整源视频划分为 %d 个连续叙事片段，共 %.1f 秒；高光仅用于标注重点，不裁掉普通内容"
                            .formatted(clips.size(), totalSeconds);
            Path output = visualAnalysisPath.getParent().resolve("highlights.json");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("strategy", highlightsOnly ? "ranked-highlights-v1"
                    : (hints.isEmpty() ? "full-story-v1" : "ai-guided-full-story-v1"));
            result.put("editingScope", highlightsOnly ? "HIGHLIGHTS" : "FULL_VIDEO");
            result.put("contentOverview", document.path("contentAnalysis").path("overview").asText(""));
            result.put("highlightStrategy", document.path("contentAnalysis").path("highlightStrategy").asText(""));
            result.put("aiHintCount", hints.size());
            result.put("summary", summary);
            result.put("requestedTargetDurationSeconds", targetDurationSeconds);
            result.put("selectedDurationSeconds", totalSeconds);
            result.put("clips", clips);
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper, output, result);
            log.info("HIGHLIGHT_SELECTION_SUCCESS candidates={} selected={} duration={} output={}",
                    frames.size(), clips.size(), totalSeconds, output);
            return new HighlightSelectionResult(summary, output.toString(), clips);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("高光筛选失败：" + exception.getMessage(), exception);
        }
    }

    private List<HighlightClip> buildHighlightClips(List<FrameUnderstanding> frames, double duration,
                                                     int targetDurationSeconds, List<HighlightHint> hints) {
        if (!Double.isFinite(duration) || duration <= 0) throw new IllegalStateException("源视频时长无效");
        double budget = Math.min(duration, Math.max(15, targetDurationSeconds));
        double clipLength = Math.min(20.0, budget);
        int wanted = Math.max(1, (int) Math.ceil(budget / clipLength));
        List<FrameUnderstanding> ranked = frames.stream()
                .sorted(Comparator.comparingInt((FrameUnderstanding frame) -> finalScore(frame, hints)).reversed())
                .toList();
        List<HighlightClip> selected = new ArrayList<>();
        for (FrameUnderstanding frame : ranked) {
            if (selected.size() >= wanted) break;
            double start = Math.max(0, Math.min(duration - clipLength, frame.timestampSeconds() - clipLength / 2));
            double end = Math.min(duration, start + Math.min(clipLength, budget - selected.size() * clipLength));
            boolean overlaps = selected.stream().anyMatch(clip -> start < clip.endSeconds() && end > clip.startSeconds());
            if (!overlaps && end > start) selected.add(new HighlightClip(frame.index(), start, end,
                    frame.timestampSeconds(), frame.eventType(), frame.description(), frame.excitementScore(),
                    finalScore(frame, hints)));
        }
        if (selected.isEmpty()) {
            FrameUnderstanding frame = ranked.get(0);
            double end = Math.min(duration, budget);
            selected.add(new HighlightClip(frame.index(), 0, end, frame.timestampSeconds(), frame.eventType(),
                    frame.description(), frame.excitementScore(), finalScore(frame, hints)));
        }
        return selected.stream().sorted(Comparator.comparingDouble(HighlightClip::startSeconds)).toList();
    }

    private List<HighlightClip> buildContinuousStoryClips(List<FrameUnderstanding> frames, double duration,
                                                           List<HighlightHint> hints) {
        if (!Double.isFinite(duration) || duration <= 0) throw new IllegalStateException("源视频时长无效");
        List<HighlightClip> clips = new ArrayList<>();
        for (double start = 0; start < duration; start += STORY_SEGMENT_SECONDS) {
            double end = Math.min(duration, start + STORY_SEGMENT_SECONDS);
            double segmentStart = start;
            double segmentEnd = end;
            FrameUnderstanding anchor = frames.stream()
                    .filter(frame -> frame.timestampSeconds() >= segmentStart && frame.timestampSeconds() < segmentEnd)
                    .max(Comparator.comparingInt((FrameUnderstanding frame) -> finalScore(frame, hints)))
                    .orElseGet(() -> frames.stream().min(Comparator.comparingDouble(frame ->
                            Math.abs(frame.timestampSeconds() - ((segmentStart + segmentEnd) / 2.0)))).orElseThrow());
            clips.add(new HighlightClip(anchor.index(), segmentStart, segmentEnd, anchor.timestampSeconds(),
                    anchor.eventType(), anchor.description(), anchor.excitementScore(), finalScore(anchor, hints)));
        }
        return clips;
    }

    private int finalScore(FrameUnderstanding frame, List<HighlightHint> hints) {
        return Math.min(100, frame.excitementScore() + eventBonus(frame.eventType()) + hintBonus(frame, hints));
    }

    private int hintBonus(FrameUnderstanding frame, List<HighlightHint> hints) {
        return hints.stream()
                .filter(hint -> Math.abs(hint.timestampSeconds() - frame.timestampSeconds()) <= 8.0)
                .mapToInt(hint -> Math.max(0, Math.min(25, hint.importance() / 4)))
                .max().orElse(0);
    }

    private int eventBonus(String eventType) {
        if (eventType == null) return 0;
        return switch (eventType.trim()) {
            case "胜利" -> 15;
            case "战斗", "失败" -> 10;
            case "剧情" -> 8;
            case "探索" -> 4;
            default -> 0;
        };
    }
}
