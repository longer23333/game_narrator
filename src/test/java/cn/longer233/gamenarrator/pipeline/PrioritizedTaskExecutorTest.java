package cn.longer233.gamenarrator.pipeline;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PrioritizedTaskExecutorTest {
    @Test
    void coalescesDuplicateSubmissionsAndRemovesCompletedWork() throws Exception {
        var executor = new PrioritizedTaskExecutor();
        UUID id = UUID.randomUUID();
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        assertThat(executor.submit(id, 0, () -> {
            try { release.await(1, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            finished.countDown();
        })).isTrue();
        assertThat(executor.submit(id, 100, () -> { })).isFalse();
        release.countDown();
        assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (executor.activeCount() != 0 && System.nanoTime() < deadline) Thread.onSpinWait();
        assertThat(executor.submit(id, 10, () -> { })).isTrue();
        executor.close();
        assertThat(executor.submit(UUID.randomUUID(), 0, () -> { })).isFalse();
    }

    @Test
    void closingWithQueuedWorkDoesNotLeakSubmissionState() throws Exception {
        var executor = new PrioritizedTaskExecutor();
        UUID id = UUID.randomUUID();
        assertThat(executor.submit(id, 0, () -> { })).isTrue();

        executor.close();

        Thread.sleep(25);
        assertThat(executor.queueSize()).isZero();
        assertThat(executor.submit(id, 0, () -> { })).isFalse();
    }
}
