package cn.longer233.gamenarrator.common;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Associates external processes with the task currently executing on an engine thread. */
public final class TaskProcessRegistry {
    private static final ThreadLocal<UUID> CURRENT_TASK = new ThreadLocal<>();
    private static final Set<UUID> CANCELLED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Set<Process>> PROCESSES = new ConcurrentHashMap<>();
    private static final Object RESOURCE_MONITOR = new Object();
    private static final ConcurrentHashMap<UUID, ResourceRequest> RESERVATIONS = new ConcurrentHashMap<>();
    private static final PriorityQueue<Waiter> RESOURCE_WAITERS = new PriorityQueue<>(
            Comparator.comparingInt(Waiter::priority).reversed().thenComparingLong(Waiter::sequence));
    private static final AtomicLong WAITER_SEQUENCE = new AtomicLong();
    private static volatile ResourceRequest resourceBudget = new ResourceRequest(
            Math.max(1, Runtime.getRuntime().availableProcessors()), Long.MAX_VALUE, Long.MAX_VALUE);
    private static long reservedCpuUnits;
    private static long reservedMemoryBytes;
    private static long reservedGpuMemoryBytes;

    static {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("task-process-cleanup").unstarted(() ->
                PROCESSES.values().forEach(processes -> processes.forEach(ExternalProcessRunner::terminateTree))));
    }

    private TaskProcessRegistry() { }

    public static Scope open(UUID taskId) {
        CURRENT_TASK.set(taskId);
        return new Scope(taskId);
    }

    /** Configures the process-wide CPU/RAM/VRAM admission ceiling. */
    public static void configureResourceBudget(ResourceRequest budget) {
        synchronized (RESOURCE_MONITOR) {
            resourceBudget = budget.normalizedBudget();
            RESOURCE_MONITOR.notifyAll();
        }
    }

    /** Waits in priority order until the task can reserve its complete processing budget. */
    public static ResourceLease acquireResources(UUID taskId, int priority, ResourceRequest requested) {
        ResourceRequest demand = requested.normalizedDemand();
        if (!fits(demand, resourceBudget)) {
            throw new ResourceAdmissionException("任务资源需求超过全局预算，无法进入处理队列");
        }
        Waiter waiter = new Waiter(taskId, priority, WAITER_SEQUENCE.incrementAndGet(), demand);
        synchronized (RESOURCE_MONITOR) {
            RESOURCE_WAITERS.add(waiter);
            try {
                while (RESOURCE_WAITERS.peek() == null
                        || !RESOURCE_WAITERS.peek().taskId().equals(taskId) || !hasAvailable(demand)) {
                    throwIfCancelled(taskId);
                    try { RESOURCE_MONITOR.wait(250); }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new CancellationException("等待任务资源时被中断");
                    }
                }
                RESOURCE_WAITERS.removeIf(item -> item.taskId().equals(taskId));
                RESERVATIONS.put(taskId, demand);
                reservedCpuUnits += demand.cpuUnits();
                reservedMemoryBytes += demand.memoryBytes();
                reservedGpuMemoryBytes += demand.gpuMemoryBytes();
                return new ResourceLease(taskId);
            } catch (RuntimeException failure) {
                RESOURCE_WAITERS.removeIf(item -> item.taskId().equals(taskId));
                RESOURCE_MONITOR.notifyAll();
                throw failure;
            }
        }
    }

    public static ResourceSnapshot resourceSnapshot() {
        synchronized (RESOURCE_MONITOR) {
            return new ResourceSnapshot(resourceBudget, reservedCpuUnits, reservedMemoryBytes,
                    reservedGpuMemoryBytes, RESERVATIONS.size(), RESOURCE_WAITERS.size());
        }
    }

    public static boolean reprioritizeResources(UUID taskId, int priority) {
        synchronized (RESOURCE_MONITOR) {
            Waiter current = RESOURCE_WAITERS.stream().filter(item -> item.taskId().equals(taskId)).findFirst().orElse(null);
            if (current == null) return false;
            RESOURCE_WAITERS.remove(current);
            RESOURCE_WAITERS.add(new Waiter(taskId, priority, current.sequence(), current.demand()));
            RESOURCE_MONITOR.notifyAll();
            return true;
        }
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
        synchronized (RESOURCE_MONITOR) { RESOURCE_MONITOR.notifyAll(); }
    }

    public static boolean cancelAndAwait(UUID taskId, Duration timeout) {
        cancel(taskId);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Set<Process> processes = PROCESSES.getOrDefault(taskId, Set.of());
            if (processes.stream().noneMatch(Process::isAlive)) return true;
            processes.stream().filter(Process::isAlive).forEach(ExternalProcessRunner::terminateTree);
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return PROCESSES.getOrDefault(taskId, Set.of()).stream().noneMatch(Process::isAlive);
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

    public record ResourceRequest(int cpuUnits, long memoryBytes, long gpuMemoryBytes) {
        private ResourceRequest normalizedBudget() {
            return new ResourceRequest(Math.max(1, cpuUnits), positiveOrUnlimited(memoryBytes),
                    positiveOrUnlimited(gpuMemoryBytes));
        }
        private ResourceRequest normalizedDemand() {
            return new ResourceRequest(Math.max(1, cpuUnits), Math.max(0, memoryBytes), Math.max(0, gpuMemoryBytes));
        }
        private static long positiveOrUnlimited(long value) { return value <= 0 ? Long.MAX_VALUE : value; }
    }

    public record ResourceSnapshot(ResourceRequest budget, long reservedCpuUnits, long reservedMemoryBytes,
                                   long reservedGpuMemoryBytes, int activeTasks, int waitingTasks) { }

    public static final class ResourceLease implements AutoCloseable {
        private final UUID taskId;
        private boolean closed;
        private ResourceLease(UUID taskId) { this.taskId = taskId; }
        @Override public void close() {
            synchronized (RESOURCE_MONITOR) {
                if (closed) return;
                closed = true;
                ResourceRequest released = RESERVATIONS.remove(taskId);
                if (released != null) {
                    reservedCpuUnits -= released.cpuUnits();
                    reservedMemoryBytes -= released.memoryBytes();
                    reservedGpuMemoryBytes -= released.gpuMemoryBytes();
                }
                RESOURCE_MONITOR.notifyAll();
            }
        }
    }

    private static boolean hasAvailable(ResourceRequest demand) {
        return demand.cpuUnits() <= resourceBudget.cpuUnits() - reservedCpuUnits
                && demand.memoryBytes() <= resourceBudget.memoryBytes() - reservedMemoryBytes
                && demand.gpuMemoryBytes() <= resourceBudget.gpuMemoryBytes() - reservedGpuMemoryBytes;
    }
    private static boolean fits(ResourceRequest demand, ResourceRequest budget) {
        return demand.cpuUnits() <= budget.cpuUnits() && demand.memoryBytes() <= budget.memoryBytes()
                && demand.gpuMemoryBytes() <= budget.gpuMemoryBytes();
    }
    private record Waiter(UUID taskId, int priority, long sequence, ResourceRequest demand) { }
}
