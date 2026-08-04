package cn.longer233.gamenarrator.highlight;

import cn.longer233.gamenarrator.vision.FrameUnderstanding;
import cn.longer233.gamenarrator.vision.HighlightHint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Files;
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
            FeatureContext features = loadFeatures(visualAnalysisPath, document.path("transcriptText").asText(""));
            boolean highlightsOnly = "HIGHLIGHTS".equalsIgnoreCase(editingScope);
            List<HighlightClip> clips = highlightsOnly
                    ? buildHighlightClips(frames, videoDurationSeconds, targetDurationSeconds, hints, features)
                    : buildContinuousStoryClips(frames, videoDurationSeconds, hints, features);
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
            result.put("scoringStrategy", "multimodal-v2");
            result.put("featureWeights", Map.of("visual", 55, "event", 15, "audio", 15, "ocr", 10, "transcript", 5));
            result.put("audioFeatureAvailable", !features.audioWindows().isEmpty());
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
                                                     int targetDurationSeconds, List<HighlightHint> hints,
                                                     FeatureContext features) {
        if (!Double.isFinite(duration) || duration <= 0) throw new IllegalStateException("源视频时长无效");
        double budget = Math.min(duration, Math.max(15, targetDurationSeconds));
        double clipLength = Math.min(20.0, budget);
        int wanted = Math.max(1, (int) Math.ceil(budget / clipLength));
        List<FrameUnderstanding> ranked = frames.stream()
                .sorted(Comparator.comparingInt((FrameUnderstanding frame) -> finalScore(frame, hints, features)).reversed())
                .toList();
        List<HighlightClip> selected = new ArrayList<>();
        for (FrameUnderstanding frame : ranked) {
            if (selected.size() >= wanted) break;
            double start = Math.max(0, Math.min(duration - clipLength, frame.timestampSeconds() - clipLength / 2));
            double end = Math.min(duration, start + Math.min(clipLength, budget - selected.size() * clipLength));
            boolean overlaps = selected.stream().anyMatch(clip -> start < clip.endSeconds() && end > clip.startSeconds());
            if (!overlaps && end > start) selected.add(new HighlightClip(frame.index(), start, end,
                    frame.timestampSeconds(), frame.eventType(), frame.description(), frame.excitementScore(),
                    finalScore(frame, hints, features)));
        }
        if (selected.isEmpty()) {
            FrameUnderstanding frame = ranked.get(0);
            double end = Math.min(duration, budget);
            selected.add(new HighlightClip(frame.index(), 0, end, frame.timestampSeconds(), frame.eventType(),
                    frame.description(), frame.excitementScore(), finalScore(frame, hints, features)));
        }
        return selected.stream().sorted(Comparator.comparingDouble(HighlightClip::startSeconds)).toList();
    }

    private List<HighlightClip> buildContinuousStoryClips(List<FrameUnderstanding> frames, double duration,
                                                           List<HighlightHint> hints, FeatureContext features) {
        if (!Double.isFinite(duration) || duration <= 0) throw new IllegalStateException("源视频时长无效");
        List<HighlightClip> clips = new ArrayList<>();
        for (double start = 0; start < duration; start += STORY_SEGMENT_SECONDS) {
            double end = Math.min(duration, start + STORY_SEGMENT_SECONDS);
            double segmentStart = start;
            double segmentEnd = end;
            FrameUnderstanding anchor = frames.stream()
                    .filter(frame -> frame.timestampSeconds() >= segmentStart && frame.timestampSeconds() < segmentEnd)
                    .max(Comparator.comparingInt((FrameUnderstanding frame) -> finalScore(frame, hints, features)))
                    .orElseGet(() -> frames.stream().min(Comparator.comparingDouble(frame ->
                            Math.abs(frame.timestampSeconds() - ((segmentStart + segmentEnd) / 2.0)))).orElseThrow());
            clips.add(new HighlightClip(anchor.index(), segmentStart, segmentEnd, anchor.timestampSeconds(),
                    anchor.eventType(), anchor.description(), anchor.excitementScore(), finalScore(anchor, hints, features)));
        }
        return clips;
    }

    private int finalScore(FrameUnderstanding frame, List<HighlightHint> hints, FeatureContext features) {
        int visual = Math.min(100, frame.excitementScore() + hintBonus(frame, hints));
        int event = Math.min(100, eventBonus(frame.eventType()) * 6);
        int audio = features.audioScore(frame.timestampSeconds());
        int ocr = keywordScore(frame.ocrText());
        int transcript = features.transcriptScore(frame.timestampSeconds(), this::keywordScore);
        return Math.min(100, (int) Math.round(visual * .55 + event * .15 + audio * .15 + ocr * .10 + transcript * .05));
    }

    private int keywordScore(String text) {
        if (text == null || text.isBlank()) return 0;
        String value = text.toLowerCase();
        return List.of("胜利", "失败", "击杀", "连杀", "得分", "boss", "victory", "defeat", "kill", "win")
                .stream().anyMatch(value::contains) ? 100 : 25;
    }

    private FeatureContext loadFeatures(Path visualAnalysisPath, String transcriptText) {
        Path audio = visualAnalysisPath.resolveSibling("audio-analysis.json");
        List<TranscriptWindow> transcriptWindows = loadTranscriptWindows(visualAnalysisPath.resolveSibling("transcript.json"));
        if (!Files.isRegularFile(audio)) return new FeatureContext(List.of(), transcriptWindows, transcriptText);
        try {
            JsonNode root = objectMapper.readTree(audio.toFile());
            List<AudioWindow> windows = new ArrayList<>();
            for (JsonNode item : root.path("windows")) windows.add(new AudioWindow(item.path("startSeconds").asDouble(),
                    item.path("endSeconds").asDouble(), item.path("energyScore").asInt(), item.path("clipping").asBoolean()));
            return new FeatureContext(windows, transcriptWindows, transcriptText);
        } catch (Exception exception) {
            log.warn("HIGHLIGHT_AUDIO_FEATURES_SKIPPED path={} reason={}", audio, exception.getMessage());
            return new FeatureContext(List.of(), transcriptWindows, transcriptText);
        }
    }

    private List<TranscriptWindow> loadTranscriptWindows(Path transcriptPath) {
        if (!Files.isRegularFile(transcriptPath)) return List.of();
        try {
            List<TranscriptWindow> result = new ArrayList<>();
            for (JsonNode item : objectMapper.readTree(transcriptPath.toFile()).path("transcription")) {
                double start = item.path("offsets").path("from").asDouble() / 1000.0;
                double end = item.path("offsets").path("to").asDouble() / 1000.0;
                result.add(new TranscriptWindow(start, Math.max(start, end), item.path("text").asText("")));
            }
            return result;
        } catch (Exception exception) {
            log.debug("HIGHLIGHT_TRANSCRIPT_FEATURES_SKIPPED path={} reason={}", transcriptPath, exception.getMessage());
            return List.of();
        }
    }

    private record AudioWindow(double start, double end, int energy, boolean clipping) { }
    private record TranscriptWindow(double start, double end, String text) { }
    private record FeatureContext(List<AudioWindow> audioWindows, List<TranscriptWindow> transcriptWindows,
                                  String transcriptText) {
        int audioScore(double timestamp) {
            return audioWindows.stream().filter(item -> timestamp >= item.start() && timestamp < item.end())
                    .mapToInt(item -> item.clipping() ? Math.max(0, item.energy() - 25) : item.energy()).max().orElse(0);
        }
        int transcriptScore(double timestamp, java.util.function.ToIntFunction<String> scorer) {
            return transcriptWindows.stream().filter(item -> timestamp >= item.start() && timestamp <= item.end())
                    .mapToInt(item -> scorer.applyAsInt(item.text())).max().orElseGet(() -> scorer.applyAsInt(transcriptText));
        }
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
