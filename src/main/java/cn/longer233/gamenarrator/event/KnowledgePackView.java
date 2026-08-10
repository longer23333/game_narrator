package cn.longer233.gamenarrator.event;

import java.time.OffsetDateTime;

public record KnowledgePackView(String code, String name, String description, int formatVersion,
                                boolean builtIn, boolean active, OffsetDateTime importedAt) {
}
