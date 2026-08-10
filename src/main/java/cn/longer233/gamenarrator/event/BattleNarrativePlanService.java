package cn.longer233.gamenarrator.event;

import cn.longer233.gamenarrator.script.ScriptWorkspaceService;
import cn.longer233.gamenarrator.script.StoryboardSegmentView;
import cn.longer233.gamenarrator.script.StoryboardView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class BattleNarrativePlanService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final GameEventTimelineService events;
    private final ScriptWorkspaceService workspace;

    public BattleNarrativePlanService(JdbcTemplate jdbc, ObjectMapper mapper,
                                      GameEventTimelineService events, ScriptWorkspaceService workspace) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.events = events;
        this.workspace = workspace;
    }

    @Transactional
    public BattleNarrativePlanView generate(UUID taskId) {
        List<GameEventView> confirmed = confirmedEvents(taskId);
        if (confirmed.isEmpty()) throw new IllegalStateException("请先确认至少一个游戏事件，再生成战局叙事结构");
        StoryboardView board = workspace.storyboard(taskId);
        Set<UUID> used = new LinkedHashSet<>();
        GameEventView result = pick(confirmed, used, "defeated|victory|result|clear|settlement", false);
        GameEventView reversal = pick(confirmed, used, "phase|transition|reversal|comeback", false);
        GameEventView crisis = pick(confirmed, used, "critical|danger|death|low_health|player_critical", false);
        GameEventView setup = pick(confirmed, used, "", true);
        GameEventView climax = highest(confirmed, used);
        List<NarrativeBeat> beats = List.of(
                beat(NarrativeStage.SETUP, "铺垫", "建立局势与目标", setup, board),
                beat(NarrativeStage.CRISIS, "危机", "突出风险并压缩安全感", crisis, board),
                beat(NarrativeStage.REVERSAL, "转折", "呈现阶段变化或局势逆转", reversal, board),
                beat(NarrativeStage.CLIMAX, "高潮", "集中最高价值的关键行动", climax, board),
                beat(NarrativeStage.RESULT, "结果", "明确收束已确认的战斗结果", result, board));
        String fingerprint = fingerprint(confirmed);
        OffsetDateTime now = OffsetDateTime.now();
        BattleNarrativePlanView plan = new BattleNarrativePlanView(taskId, 1,
                "CONFIRMED_EVENT_FIVE_ACT", fingerprint, beats, false, now, null);
        try {
            jdbc.update("""
                    MERGE INTO battle_narrative_plan(task_id,plan_json,confirmed_event_fingerprint,applied,generated_at,applied_at)
                    KEY(task_id) VALUES(?,?,?,?,?,NULL)
                    """, taskId, mapper.writeValueAsString(plan), fingerprint, false, now);
            return plan;
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存战局叙事结构", exception);
        }
    }

    public BattleNarrativePlanView find(UUID taskId) {
        events.list(taskId);
        return jdbc.query("SELECT plan_json,applied,applied_at FROM battle_narrative_plan WHERE task_id=?",
                (rs, row) -> {
                    try {
                        BattleNarrativePlanView stored = mapper.readValue(rs.getString("plan_json"), BattleNarrativePlanView.class);
                        return new BattleNarrativePlanView(stored.taskId(), stored.version(), stored.strategy(),
                                stored.confirmedEventFingerprint(), stored.beats(), rs.getBoolean("applied"),
                                stored.generatedAt(), rs.getObject("applied_at", OffsetDateTime.class));
                    } catch (Exception exception) {
                        throw new IllegalStateException("无法读取战局叙事结构", exception);
                    }
                }, taskId).stream().findFirst().orElse(new BattleNarrativePlanView(
                        taskId, 1, "CONFIRMED_EVENT_FIVE_ACT", "", List.of(), false, null, null));
    }

    @Transactional
    public BattleNarrativePlanView apply(UUID taskId) {
        BattleNarrativePlanView plan = find(taskId);
        if (plan.beats().isEmpty()) throw new IllegalStateException("请先生成战局叙事结构");
        if (plan.applied()) throw new IllegalStateException("该战局叙事结构已经应用，请在事件变化后重新生成计划");
        String currentFingerprint = fingerprint(confirmedEvents(taskId));
        if (!currentFingerprint.equals(plan.confirmedEventFingerprint())) {
            throw new IllegalStateException("已确认事件发生变化，请重新生成战局叙事结构后再应用");
        }
        workspace.applyNarrativeStructure(taskId, plan.beats());
        OffsetDateTime appliedAt = OffsetDateTime.now();
        jdbc.update("UPDATE battle_narrative_plan SET applied=TRUE,applied_at=? WHERE task_id=?", appliedAt, taskId);
        return new BattleNarrativePlanView(plan.taskId(), plan.version(), plan.strategy(),
                plan.confirmedEventFingerprint(), plan.beats(), true, plan.generatedAt(), appliedAt);
    }

    /** Applies reviewed AI overrides only inside the already verified five-act fact structure. */
    @Transactional
    public BattleNarrativePlanView applyReviewed(UUID taskId, JsonNode decision) {
        BattleNarrativePlanView plan = find(taskId);
        if (plan.beats().isEmpty()) plan = generate(taskId);
        if (plan.applied()) throw new IllegalStateException("该战局叙事结构已经应用");
        if (!fingerprint(confirmedEvents(taskId)).equals(plan.confirmedEventFingerprint())) {
            throw new IllegalStateException("已确认事件已经变化，请重新召开导演评审会");
        }
        Set<Integer> availableClips = workspace.storyboard(taskId).segments().stream()
                .map(StoryboardSegmentView::clipIndex).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, JsonNode> overrides = new java.util.HashMap<>();
        if (decision != null && decision.path("narrativeBeats").isArray()) {
            decision.path("narrativeBeats").forEach(node -> overrides.put(node.path("stage").asText().toUpperCase(Locale.ROOT), node));
        }
        List<NarrativeBeat> reviewed = plan.beats().stream().map(beat -> {
            JsonNode override = overrides.get(beat.stage().name());
            if (override == null) return beat;
            int requestedClip = override.path("clipIndex").asInt(beat.clipIndex() == null ? -1 : beat.clipIndex());
            Integer clip = availableClips.contains(requestedClip) ? requestedClip : beat.clipIndex();
            double pace = bounded(override.path("paceMultiplier").asDouble(beat.paceMultiplier()), .5, 1.8);
            double music = bounded(override.path("musicIntensity").asDouble(beat.musicIntensity()), 0, 1);
            String directive = override.path("narrationDirective").asText(beat.narrationDirective()).strip();
            if (directive.isBlank()) directive = beat.narrationDirective();
            if (directive.length() > 500) directive = directive.substring(0, 500);
            return new NarrativeBeat(beat.stage(), beat.label(), beat.objective(), beat.eventId(), beat.eventType(),
                    beat.confirmedFact(), clip, beat.sourceStartSeconds(), beat.sourceEndSeconds(), pace, music,
                    directive, beat.structuralPlaceholder());
        }).toList();
        workspace.applyNarrativeStructure(taskId, reviewed);
        OffsetDateTime appliedAt = OffsetDateTime.now();
        jdbc.update("UPDATE battle_narrative_plan SET applied=TRUE,applied_at=? WHERE task_id=?", appliedAt, taskId);
        return new BattleNarrativePlanView(plan.taskId(), plan.version(), "AI_DIRECTOR_REVIEWED",
                plan.confirmedEventFingerprint(), reviewed, true, plan.generatedAt(), appliedAt);
    }

    private double bounded(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private List<GameEventView> confirmedEvents(UUID taskId) {
        return events.list(taskId).stream()
                .filter(item -> "CONFIRMED".equals(item.confirmationStatus()))
                .sorted(Comparator.comparingDouble(GameEventView::startSeconds)).toList();
    }

    private GameEventView pick(List<GameEventView> values, Set<UUID> used, String expression, boolean earliest) {
        if (earliest) return values.stream().filter(item -> !used.contains(item.id())).findFirst()
                .map(item -> mark(item, used)).orElse(null);
        String[] needles = expression.split("\\|");
        return values.stream().filter(item -> !used.contains(item.id()))
                .filter(item -> containsAny(item.eventType().toLowerCase(Locale.ROOT), needles))
                .max(Comparator.comparingInt(GameEventView::importance)).map(item -> mark(item, used)).orElse(null);
    }

    private GameEventView highest(List<GameEventView> values, Set<UUID> used) {
        return values.stream().filter(item -> !used.contains(item.id()))
                .max(Comparator.comparingInt(GameEventView::importance))
                .map(item -> mark(item, used)).orElse(null);
    }

    private GameEventView mark(GameEventView event, Set<UUID> used) { used.add(event.id()); return event; }
    private boolean containsAny(String text, String[] values) {
        for (String value : values) if (!value.isBlank() && text.contains(value)) return true;
        return false;
    }

    private NarrativeBeat beat(NarrativeStage stage, String label, String objective,
                               GameEventView event, StoryboardView board) {
        double pace = switch (stage) { case SETUP -> .90; case CRISIS -> 1.08; case REVERSAL -> 1.18; case CLIMAX -> 1.28; case RESULT -> .82; };
        double music = switch (stage) { case SETUP -> .28; case CRISIS -> .62; case REVERSAL -> .76; case CLIMAX -> 1.0; case RESULT -> .38; };
        String directive = switch (stage) {
            case SETUP -> "用一句话建立局势，不提前泄露结果";
            case CRISIS -> "缩短句子，强调压力，但不添加未确认伤害或数值";
            case REVERSAL -> "突出变化前后对比，只引用已确认事实";
            case CLIMAX -> "使用高密度短句，把关键动作放在句尾";
            case RESULT -> "降低语速并明确收束，只陈述用户确认的结果";
        };
        if (event == null) return new NarrativeBeat(stage, label, objective, null, null,
                null, null, 0, 0, pace, music, directive, true);
        Integer clipIndex = board.segments().stream()
                .filter(clip -> overlaps(clip, event))
                .max(Comparator.comparingInt(StoryboardSegmentView::finalScore))
                .map(StoryboardSegmentView::clipIndex).orElse(null);
        return new NarrativeBeat(stage, label, objective, event.id(), event.eventType(), event.description(),
                clipIndex, event.startSeconds(), event.endSeconds(), pace, music, directive, false);
    }

    private boolean overlaps(StoryboardSegmentView clip, GameEventView event) {
        return clip.startSeconds() < event.endSeconds() && clip.endSeconds() > event.startSeconds();
    }

    private String fingerprint(List<GameEventView> confirmed) {
        try {
            StringBuilder value = new StringBuilder();
            confirmed.forEach(item -> value.append(item.id()).append('|').append(item.eventType()).append('|')
                    .append(item.description()).append('|').append(item.importance()).append('|')
                    .append(item.startSeconds()).append('|').append(item.endSeconds()).append(';'));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("无法计算已确认事件版本", exception); }
    }
}
