package cn.longer233.gamenarrator.event;

import java.util.List;

public record BossBattleKnowledgePack(
        String code,
        String name,
        String description,
        List<EventRule> eventRules,
        Integer formatVersion
) {
    public record EventRule(
            String code,
            String name,
            List<String> keywords,
            double baseConfidence,
            int importance
    ) {
    }
}
