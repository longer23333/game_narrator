package cn.longer233.gamenarrator.task.web;

import cn.longer233.gamenarrator.task.application.VideoTaskView;

import java.util.List;
import java.util.UUID;

public record TaskStreamMessage(String type, List<VideoTaskView> tasks,
                                List<UUID> removedIds, long emittedAt) {
    static TaskStreamMessage snapshot(List<VideoTaskView> tasks, long now) {
        return new TaskStreamMessage("snapshot", List.copyOf(tasks), List.of(), now);
    }

    static TaskStreamMessage heartbeat(long now) {
        return new TaskStreamMessage("heartbeat", List.of(), List.of(), now);
    }
}
