package cn.longer233.gamenarrator.editor;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectRevisionView(UUID id, int revisionNo, UUID parentRevisionId, String label,
                                  String changeType, String changeSummary, OffsetDateTime createdAt,
                                  boolean current, int childCount) { }
