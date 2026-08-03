package cn.longer233.gamenarrator.common;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

/** Associates external processes with the task currently executing on an engine thread. */
public final class TaskProcessRegistry {
    private static final ThreadLocal<UUID> CURRENT_TASK = new ThreadLocal<>();
    private static final Set<UUID> CANCELLED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Set<Process>> PROCESSES = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("task-process-cleanup").unstarted(() ->
                PROCESSES.values().forEach(processes -> processes.forEach(ExternalProcessRunner::terminateTree))));
    }

    private TaskProcessRegistry() { }

    public static Scope open(UUID taskId) {
        CURRENT_TASK.set(taskId);
        return new Scope(taskId);
    }

    public static void register(Process process) {
        UUID taskId = CURRENT_TASK.get();
        if (taskId == null) return;
        if (CANCELLED.contains(taskId)) {
            ExternalProcessRunner.terminateTree(process);
            throw new CancellationException("任务已取消");
        }
        PROCESSES.computeIfAbsent(taskId, ignored -> ConcurrentHashMap.newKeySet()).add(process);
        if (CANCELLED.contains(taskId)) {
            ExternalProcessRunner.terminateTree(process);
            throw new CancellationException("任务已取消");
        }
    }

    public static void unregister(Process process) {
        UUID taskId = CURRENT_TASK.get();
        if (taskId == null) return;
        Set<Process> processes = PROCESSES.get(taskId);
        if (processes != null) processes.remove(process);
    }

    public static void cancel(UUID taskId) {
        CANCELLED.add(taskId);
        PROCESSES.getOrDefault(taskId, Set.of()).forEach(ExternalProcessRunner::terminateTree);
    }

    public static void throwIfCancelled(UUID taskId) {
        if (CANCELLED.contains(taskId)) throw new CancellationException("用户取消了任务");
    }

    public static boolean currentTaskCancelled() {
        UUID taskId = CURRENT_TASK.get();
        return taskId != null && CANCELLED.contains(taskId);
    }

    public static final class Scope implements AutoCloseable {
        private final UUID taskId;
        private Scope(UUID taskId) { this.taskId = taskId; }
        @Override public void close() {
            PROCESSES.remove(taskId);
            CANCELLED.remove(taskId);
            CURRENT_TASK.remove();
        }
    }
}
