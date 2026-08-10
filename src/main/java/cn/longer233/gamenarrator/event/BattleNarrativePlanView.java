package cn.longer233.gamenarrator.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BattleNarrativePlanView(
        UUID taskId,
        int version,
        String strategy,
        String confirmedEventFingerprint,
        List<NarrativeBeat> beats,
        boolean applied,
        OffsetDateTime generatedAt,
        OffsetDateTime appliedAt
) { }
