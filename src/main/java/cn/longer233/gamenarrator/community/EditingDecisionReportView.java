package cn.longer233.gamenarrator.community;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EditingDecisionReportView(UUID id, UUID taskId, JsonNode report, String markdown,
        int reportVersion, OffsetDateTime generatedAt) { }
