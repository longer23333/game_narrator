package cn.longer233.gamenarrator.task.repository;

import java.time.Instant;

/** Lightweight owner-level token used to avoid rebuilding unchanged task-list views. */
public record TaskListRevision(long taskCount, Instant taskUpdatedAt, Instant stageUpdatedAt) {
}
