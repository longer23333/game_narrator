package cn.longer233.gamenarrator.performance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrent-performance;DB_CLOSE_DELAY=-1",
        "game-narrator.storage-root=./target/concurrent-performance-storage",
        "game-narrator.media-import.yt-dlp=./mvnw.cmd"
})
@AutoConfigureMockMvc
class ConcurrentApiPerformanceTest {
    @Autowired MockMvc mvc;

    @Test
    void concurrentTaskListQueriesStayResponsiveAndHeapReturnsNearBaseline() throws Exception {
        forceGc();
        long before = usedHeap();
        int requests = 240;
        var elapsed = java.util.Collections.synchronizedList(new ArrayList<Long>());
        var failures = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        try (var executor = Executors.newFixedThreadPool(12)) {
            for (int i = 0; i < requests; i++) {
                int requestIndex = i;
                executor.submit(() -> {
                long started = System.nanoTime();
                try {
                    String path = requestIndex % 2 == 0 ? "/api/tasks" : "/api/export-presets";
                    mvc.perform(get(path)).andExpect(status().isOk());
                } catch (Exception exception) {
                    failures.add(exception);
                }
                elapsed.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
                });
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
        elapsed.sort(Long::compareTo);
        long p95 = elapsed.get((int) Math.ceil(elapsed.size() * .95) - 1);
        assertThat(elapsed).hasSize(requests);
        assertThat(failures).isEmpty();
        assertThat(p95).as("task-list p95 milliseconds").isLessThan(750L);
        forceGc();
        long growth = usedHeap() - before;
        assertThat(growth).as("retained heap after repeated concurrent queries").isLessThan(32L * 1024 * 1024);
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(100);
    }
}
