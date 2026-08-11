package cn.longer233.gamenarrator.event;

import java.util.UUID;

/** Published inside the event-edit transaction and consumed only after a successful commit. */
public record ConfirmedGameEventChanged(UUID taskId, UUID eventId) { }
