package cn.longer233.gamenarrator.community;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record CreativeAssistantSessionView(UUID id, String brief, JsonNode plans, String status,
                                           String selectedType, UUID generatedTaskId) { }
