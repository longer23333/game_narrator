package cn.longer233.gamenarrator.community;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreativeVariantView(UUID id, UUID sourceTaskId, String variantType, String name,
        JsonNode strategy, String status, UUID generatedTaskId, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
