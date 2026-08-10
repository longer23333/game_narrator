package cn.longer233.gamenarrator.event;

import java.util.UUID;

public record NarrativeBeat(
        NarrativeStage stage,
        String label,
        String objective,
        UUID eventId,
        String eventType,
        String confirmedFact,
        Integer clipIndex,
        double sourceStartSeconds,
        double sourceEndSeconds,
        double paceMultiplier,
        double musicIntensity,
        String narrationDirective,
        boolean structuralPlaceholder
) { }
