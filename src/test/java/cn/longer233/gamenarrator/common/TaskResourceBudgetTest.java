package cn.longer233.gamenarrator.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class TaskResourceBudgetTest {
    @AfterEach
    void restoreBudget() {
        TaskProcessRegistry.configureResourceBudget(new TaskProcessRegistry.ResourceRequest(
                Runtime.getRuntime().availableProcessors(), Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void queuesUntilCpuMemoryAndGpuReservationIsReleased() throws Exception {
        TaskProcessRegistry.configureResourceBudget(new TaskProcessRegistry.ResourceRequest(1, 100, 100));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var request = new TaskProcessRegistry.ResourceRequest(1, 80, 90);
        var firstLease = TaskProcessRegistry.acquireResources(first, 0, request);
        CountDownLatch acquired = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var waiting = executor.submit(() -> {
            try (var ignored = TaskProcessRegistry.acquireResources(second, 10, request)) {
                acquired.countDown();
            }
        });

        assertThat(acquired.await(150, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(TaskProcessRegistry.resourceSnapshot().waitingTasks()).isEqualTo(1);
        firstLease.close();
        assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();
        waiting.get(2, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(TaskProcessRegistry.resourceSnapshot().activeTasks()).isZero();
    }

    @Test
    void rejectsTaskWhoseDemandCanNeverFitBudget() {
        TaskProcessRegistry.configureResourceBudget(new TaskProcessRegistry.ResourceRequest(1, 100, 100));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TaskProcessRegistry.acquireResources(
                UUID.randomUUID(), 0, new TaskProcessRegistry.ResourceRequest(2, 50, 50)))
                .isInstanceOf(ResourceAdmissionException.class)
                .hasMessageContaining("超过全局预算");
    }

    @Test
    void grantsWaitingReservationsByPriorityAndSupportsManualPromotion() throws Exception {
        TaskProcessRegistry.configureResourceBudget(new TaskProcessRegistry.ResourceRequest(1, 100, 100));
        var request = new TaskProcessRegistry.ResourceRequest(1, 50, 50);
        var blocker = TaskProcessRegistry.acquireResources(UUID.randomUUID(), 0, request);
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        var order = new CopyOnWriteArrayList<UUID>();
        var executor = Executors.newFixedThreadPool(2);
        var lowFuture = executor.submit(() -> {
            try (var ignored = TaskProcessRegistry.acquireResources(low, -10, request)) { order.add(low); }
        });
        var highFuture = executor.submit(() -> {
            try (var ignored = TaskProcessRegistry.acquireResources(high, 50, request)) { order.add(high); }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (TaskProcessRegistry.resourceSnapshot().waitingTasks() < 2 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(TaskProcessRegistry.reprioritizeResources(low, 80)).isTrue();
        blocker.close();
        lowFuture.get(2, TimeUnit.SECONDS);
        highFuture.get(2, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(order).containsExactly(low, high);
    }
}
