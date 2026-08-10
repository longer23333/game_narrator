package cn.longer233.gamenarrator.quality;

import cn.longer233.gamenarrator.event.GameEventFact;
import cn.longer233.gamenarrator.event.GameEventTimelineService;
import cn.longer233.gamenarrator.script.ScriptWorkspaceService;
import cn.longer233.gamenarrator.script.StoryboardSegmentView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NarrativeConsistencyService {
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d])\\d+(?:\\.\\d+)?%?");
    private static final List<String> RESULT_WORDS = List.of("击败", "胜利", "通关", "defeated", "victory");
    private final GameEventTimelineService events;
    private final ScriptWorkspaceService workspace;

    public NarrativeConsistencyService(GameEventTimelineService events, ScriptWorkspaceService workspace) {
        this.events = events;
        this.workspace = workspace;
    }

    public NarrativeQualityReport inspect(UUID taskId) {
        List<GameEventFact> facts = events.confirmedFacts(taskId);
        List<StoryboardSegmentView> segments = workspace.storyboard(taskId).segments();
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
        int errors = (int) issues.stream().filter(i -> "ERROR".equals(i.severity())).count();
        int warnings = issues.size() - errors;
        int score = Math.max(0, 100 - errors * 25 - warnings * 8);
        boolean passed = errors == 0 && score >= 75;
        return new NarrativeQualityReport(taskId, score, passed, List.copyOf(issues), facts.size(),
                passed ? "叙事连续性和事实一致性检查通过" : "发现 %d 个错误、%d 个提醒".formatted(errors, warnings));
    }

    private NarrativeQualityReport.Issue issue(String type, String severity, Integer clip, String message, String evidence) {
        return new NarrativeQualityReport.Issue(type, severity, clip, message, evidence);
    }
    private void collectNumbers(String text, Set<String> target) {
        if (text == null) return; Matcher matcher = NUMBER.matcher(text); while (matcher.find()) target.add(matcher.group());
    }
    private boolean containsAny(String text, List<String> needles) { return needles.stream().anyMatch(text::contains); }
    private double wordOverlap(String left, String right) {
        Set<Integer> a = grams(left); Set<Integer> b = grams(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        long common = a.stream().filter(b::contains).count();
        return common / (double) Math.min(a.size(), b.size());
    }
    private Set<Integer> grams(String value) {
        Set<Integer> result = new HashSet<>();
        String text = value == null ? "" : value.replaceAll("\\s+", "");
        for (int i = 0; i + 1 < text.length(); i++) result.add(text.substring(i, i + 2).hashCode());
        return result;
    }
}
