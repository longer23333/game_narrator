package cn.longer233.gamenarrator.quality;

import cn.longer233.gamenarrator.event.GameEventFact;
import cn.longer233.gamenarrator.event.GameEventView;
import cn.longer233.gamenarrator.event.GameEventTimelineService;
import cn.longer233.gamenarrator.script.ScriptWorkspaceService;
import cn.longer233.gamenarrator.script.StoryboardSegmentView;
import cn.longer233.gamenarrator.pipeline.TaskArtifactLocator;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class NarrativeConsistencyService {
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d])\\d+(?:\\.\\d+)?%?");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<String> RESULT_WORDS = List.of("击败", "胜利", "通关", "defeated", "victory");
    private final GameEventTimelineService events;
    private final ScriptWorkspaceService workspace;
    private final VideoTaskRepository tasks;
    private final ObjectMapper mapper;
    private final TaskArtifactLocator artifacts;

    public NarrativeConsistencyService(GameEventTimelineService events, ScriptWorkspaceService workspace,
                                       VideoTaskRepository tasks, ObjectMapper mapper, TaskArtifactLocator artifacts) {
        this.events = events;
        this.workspace = workspace;
        this.tasks = tasks;
        this.mapper = mapper;
        this.artifacts = artifacts;
    }

    public NarrativeQualityReport inspect(UUID taskId) {
        List<GameEventView> confirmedEvents = events.list(taskId).stream()
                .filter(event -> "CONFIRMED".equals(event.confirmationStatus())).toList();
        List<GameEventFact> facts = confirmedEvents.stream().map(event -> new GameEventFact(event.eventType(),
                event.description(), event.startSeconds(), event.endSeconds(), event.importance())).toList();
        List<StoryboardSegmentView> segments = workspace.storyboard(taskId).segments();
        tasks.findById(taskId).orElseThrow();
        List<NarrativeQualityReport.Issue> issues = new ArrayList<>();
        boolean resultConfirmed = facts.stream().anyMatch(f -> containsAny(f.eventType().toLowerCase(Locale.ROOT),
                List.of("defeated", "victory", "result", "clear")));
        Set<String> allowedNumbers = new HashSet<>();
        facts.forEach(f -> collectNumbers(f.description(), allowedNumbers));
        for (int i = 0; i < segments.size(); i++) {
            StoryboardSegmentView current = segments.get(i);
            String narration = current.narration() == null ? "" : current.narration();
            if (!resultConfirmed && containsAny(narration.toLowerCase(Locale.ROOT), RESULT_WORDS)) {
                issues.add(issue("FACT_CONSISTENCY", "ERROR", current.clipIndex(),
                        "解说宣称战斗已经结束，但没有对应的已确认结果事件", narration));
            }
            Matcher matcher = NUMBER.matcher(narration);
            while (matcher.find()) if (!allowedNumbers.contains(matcher.group())) {
                issues.add(issue("FACT_CONSISTENCY", "WARNING", current.clipIndex(),
                        "解说包含未被已确认事件支持的具体数值", matcher.group()));
            }
            if (i > 0) {
                StoryboardSegmentView previous = segments.get(i - 1);
                if (current.startSeconds() + 3 < previous.startSeconds()) {
                    issues.add(issue("NARRATIVE_CONTINUITY", "WARNING", current.clipIndex(),
                            "镜头时间明显倒退，请确认这是有意的回叙", "%.2fs → %.2fs".formatted(previous.startSeconds(), current.startSeconds())));
                }
                double overlap = wordOverlap(previous.narration(), narration);
                if (overlap > .82) issues.add(issue("NARRATIVE_CONTINUITY", "WARNING", current.clipIndex(),
                        "相邻解说内容高度重复，可能造成叙事停滞", "相似度 %.0f%%".formatted(overlap * 100)));
            }
        }
        if (facts.isEmpty()) issues.add(issue("FACT_CONSISTENCY", "ERROR", null,
                "尚无已确认事件，无法完成事实一致性校验", "请先确认事件时间线"));
        Path transcriptJson = artifacts.latest(taskId, "TRANSCRIPT_DETAIL").orElse(null);
        boolean visualAvailable = artifactExists(artifacts.latest(taskId, "VISION_ANALYSIS").orElse(null));
        boolean asrAvailable = artifactExists(transcriptJson)
                || artifactExists(artifacts.latest(taskId, "TRANSCRIPT_TEXT").orElse(null));
        int supportedSegments = supportedSegmentCount(segments, facts);
        double evidenceCoverage = segments.isEmpty() ? 0 : supportedSegments / (double) segments.size();
        double confidenceTotal = 0;
        for (GameEventView event : confirmedEvents) confidenceTotal += event.confidence();
        double averageEventConfidence = confirmedEvents.isEmpty() ? 0 : confidenceTotal / confirmedEvents.size();
        double ocrEvidenceCoverage = confidenceTotal == 0 ? 0 : confirmedEvents.stream()
                .filter(event -> event.evidence().stream().anyMatch(item -> "OCR".equals(item.sourceType())))
                .mapToDouble(GameEventView::confidence).sum() / confidenceTotal;
        double knowledgeEvidenceCoverage = confidenceTotal == 0 ? 0 : confirmedEvents.stream()
                .filter(event -> event.knowledgePackCode() != null && !event.knowledgePackCode().isBlank())
                .mapToDouble(GameEventView::confidence).sum() / confidenceTotal;
        double speakerCoverage = speakerCoverage(transcriptJson, segments);
        double reliabilityWeight = .80 + (asrAvailable ? .20 : 0);
        double upstreamEvidenceReliability = clamp((averageEventConfidence * .40
                + ocrEvidenceCoverage * .20 + knowledgeEvidenceCoverage * .20
                + (asrAvailable ? speakerCoverage * .20 : 0)) / reliabilityWeight);
        if (!visualAvailable) issues.add(issue("EVIDENCE_COVERAGE", "ERROR", null,
                "缺少视觉分析证据，脚本评分不能判定为通过", "visualAnalysisPath unavailable"));
        if (!asrAvailable) issues.add(issue("EVIDENCE_COVERAGE", "WARNING", null,
                "缺少 ASR 证据，无法交叉核对语音内容", "transcript artifact unavailable"));
        if (evidenceCoverage < .5) issues.add(issue("EVIDENCE_COVERAGE", "ERROR", null,
                "少于一半的文案片段有时间重叠的已确认事件证据",
                "%d/%d segments supported".formatted(supportedSegments, segments.size())));
        else if (evidenceCoverage < .8) issues.add(issue("EVIDENCE_COVERAGE", "WARNING", null,
                "部分文案片段缺少时间重叠的已确认事件证据",
                "%d/%d segments supported".formatted(supportedSegments, segments.size())));
        if (!confirmedEvents.isEmpty() && averageEventConfidence < .6) issues.add(issue("EVENT_CONFIDENCE", "ERROR", null,
                "已确认事件的平均置信度过低", "average confidence %.2f".formatted(averageEventConfidence)));
        else if (!confirmedEvents.isEmpty() && averageEventConfidence < .75) issues.add(issue("EVENT_CONFIDENCE", "WARNING", null,
                "已确认事件的平均置信度偏低", "average confidence %.2f".formatted(averageEventConfidence)));
        if (asrAvailable && speakerCoverage < .5) issues.add(issue("SPEAKER_EVIDENCE", "WARNING", null,
                "少于一半文案片段有时间对齐的说话人证据", "speaker coverage %.0f%%".formatted(speakerCoverage * 100)));
        if (!confirmedEvents.isEmpty() && ocrEvidenceCoverage == 0) issues.add(issue("OCR_EVIDENCE", "WARNING", null,
                "已确认事件没有 OCR 交叉证据", "0/%d confirmed events".formatted(confirmedEvents.size())));
        if (!confirmedEvents.isEmpty() && knowledgeEvidenceCoverage == 0) issues.add(issue("KNOWLEDGE_EVIDENCE", "WARNING", null,
                "已确认事件没有知识包引用", "0/%d confirmed events".formatted(confirmedEvents.size())));
        int errors = 0;
        for (NarrativeQualityReport.Issue issue : issues) {
            if ("ERROR".equals(issue.severity())) errors++;
        }
        int warnings = issues.size() - errors;
        int score = Math.max(0, 100 - errors * 25 - warnings * 8);
        score = Math.min(score, (int) Math.round(upstreamEvidenceReliability * 100));
        boolean passed = errors == 0 && score >= 75;
        return new NarrativeQualityReport(taskId, score, passed, List.copyOf(issues), facts.size(),
                supportedSegments, segments.size(), evidenceCoverage,
                averageEventConfidence, speakerCoverage, ocrEvidenceCoverage, knowledgeEvidenceCoverage,
                upstreamEvidenceReliability,
                passed ? "叙事连续性和事实一致性检查通过" : "发现 %d 个错误、%d 个提醒".formatted(errors, warnings));
    }

    private int supportedSegmentCount(List<StoryboardSegmentView> segments, List<GameEventFact> facts) {
        if (segments.isEmpty() || facts.isEmpty()) return 0;
        List<GameEventFact> orderedFacts = facts.stream()
                .sorted(Comparator.comparingDouble(GameEventFact::startSeconds))
                .toList();
        List<StoryboardSegmentView> orderedSegments = segments.stream()
                .sorted(Comparator.comparingDouble(StoryboardSegmentView::startSeconds))
                .toList();
        int factIndex = 0;
        int supported = 0;
        for (StoryboardSegmentView segment : orderedSegments) {
            while (factIndex < orderedFacts.size()
                    && orderedFacts.get(factIndex).endSeconds() < segment.startSeconds()) {
                factIndex++;
            }
            for (int i = factIndex; i < orderedFacts.size()
                    && orderedFacts.get(i).startSeconds() <= segment.endSeconds(); i++) {
                if (orderedFacts.get(i).endSeconds() >= segment.startSeconds()) {
                    supported++;
                    break;
                }
            }
        }
        return supported;
    }

    private boolean artifactExists(Path value) {
        if (value == null) return false;
        try { return Files.isRegularFile(value); }
        catch (RuntimeException ignored) { return false; }
    }

    private double speakerCoverage(Path transcriptPath, List<StoryboardSegmentView> segments) {
        if (segments.isEmpty() || !artifactExists(transcriptPath)) return 0;
        try {
            JsonNode transcript = mapper.readTree(transcriptPath.toFile());
            String configured = transcript.path("speakerDiarization").path("path").asText("");
            Path speakerPath = configured.isBlank()
                    ? transcriptPath.resolveSibling("speaker-segments.json") : Path.of(configured);
            if (!Files.isRegularFile(speakerPath)) return 0;
            JsonNode speakerSegments = mapper.readTree(speakerPath.toFile()).path("segments");
            double supported = 0;
            for (StoryboardSegmentView segment : segments) {
                boolean overlap = false;
                double bestReliability = 0;
                for (JsonNode speaker : speakerSegments) {
                    double start = speaker.path("startMillis").asDouble() / 1000d;
                    double end = speaker.path("endMillis").asDouble() / 1000d;
                    if (!speaker.path("speakerType").asText("").isBlank()
                            && start <= segment.endSeconds() && end >= segment.startSeconds()) {
                        overlap = true;
                        double confidence = clamp(speaker.path("confidence").asDouble(.4));
                        String evidence = speaker.path("evidence").asText("");
                        double evidenceWeight = "NATIVE_LABEL".equals(evidence) ? 1.0
                                : "TIMELINE_OVERLAP".equals(evidence) ? .9 : "TEXT_FALLBACK".equals(evidence) ? .6 : .4;
                        bestReliability = Math.max(bestReliability, confidence * evidenceWeight);
                    }
                }
                if (overlap) supported += bestReliability;
            }
            return supported / (double) segments.size();
        } catch (Exception ignored) { return 0; }
    }

    private NarrativeQualityReport.Issue issue(String type, String severity, Integer clip, String message, String evidence) {
        return new NarrativeQualityReport.Issue(type, severity, clip, message, evidence);
    }
    private void collectNumbers(String text, Set<String> target) {
        if (text == null) return; Matcher matcher = NUMBER.matcher(text); while (matcher.find()) target.add(matcher.group());
    }
    private boolean containsAny(String text, List<String> needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private double wordOverlap(String left, String right) {
        Set<Integer> a = grams(left); Set<Integer> b = grams(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        int common = 0;
        for (Integer gram : a) if (b.contains(gram)) common++;
        return common / (double) Math.min(a.size(), b.size());
    }
    private Set<Integer> grams(String value) {
        Set<Integer> result = new HashSet<>();
        String text = value == null ? "" : WHITESPACE.matcher(value).replaceAll("");
        for (int i = 0; i + 1 < text.length(); i++) {
            result.add(31 * text.charAt(i) + text.charAt(i + 1));
        }
        return result;
    }
}
