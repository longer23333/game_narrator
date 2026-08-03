package cn.longer233.gamenarrator.task.application;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID id) {
        super("未找到视频任务：" + id);
    }
}
