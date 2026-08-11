package cn.longer233.gamenarrator.pipeline;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Priority dispatcher for complete pipelines; resource admission provides the actual concurrency bound. */
@Component
public class PrioritizedTaskExecutor {
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicInteger active = new AtomicInteger();
    private final ConcurrentHashMap<UUID, PrioritizedWork> submitted = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<PrioritizedWork> queue = new PriorityBlockingQueue<>();
    private final java.util.concurrent.ExecutorService waitingThreads = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("video-pipeline-wait-", 0).factory());
    private final Thread dispatcher;
    private volatile boolean closing;

    public PrioritizedTaskExecutor() {
        dispatcher = Thread.ofPlatform().name("video-pipeline-dispatcher").start(this::dispatch);
    }

    /** Test-compatible constructor; compute concurrency is governed by the resource budget, not this hint. */
    PrioritizedTaskExecutor(int ignoredWorkers) { this(); }

    public boolean submit(UUID taskId, int priority, Runnable action) {
        PrioritizedWork work = new PrioritizedWork(taskId, priority, sequence.incrementAndGet(), action);
        if (submitted.putIfAbsent(taskId, work) != null) return false;
        queue.add(work);
        return true;
    }

    public boolean reprioritize(UUID taskId, int priority) {
        PrioritizedWork current = submitted.get(taskId);
        if (current == null || !queue.remove(current)) return false;
        PrioritizedWork replacement = new PrioritizedWork(taskId, priority, sequence.incrementAndGet(), current.action);
        if (!submitted.replace(taskId, current, replacement)) return false;
        queue.add(replacement);
        return true;
    }

    public boolean cancelQueued(UUID taskId) {
        PrioritizedWork current = submitted.remove(taskId);
        return current != null && queue.remove(current);
    }

    public int queueSize() { return queue.size(); }
    public int activeCount() { return active.get(); }

    private void dispatch() {
        while (!closing) try {
            PrioritizedWork work = queue.take();
            waitingThreads.execute(() -> run(work));
        } catch (InterruptedException interrupted) {
            if (!closing) Thread.currentThread().interrupt();
        }
    }

    private void run(PrioritizedWork work) {
        active.incrementAndGet();
        try { work.action.run(); }
        finally {
            active.decrementAndGet();
            submitted.remove(work.taskId, work);
        }
    }

    @PreDestroy public void close() {
        closing = true;
        dispatcher.interrupt();
        waitingThreads.shutdownNow();
    }

    private record PrioritizedWork(UUID taskId, int priority, long sequence, Runnable action)
            implements Comparable<PrioritizedWork> {
        @Override public int compareTo(PrioritizedWork other) {
            int byPriority = Integer.compare(other.priority, priority);
            return byPriority != 0 ? byPriority : Long.compare(sequence, other.sequence);
        }
    }
}
