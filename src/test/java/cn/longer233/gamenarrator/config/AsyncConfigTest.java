package cn.longer233.gamenarrator.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncConfigTest {

    @Test
    void createsBoundedExecutorFromConfiguration() {
        Executor configured = new AsyncConfig().taskExecutor(2, 4, 10);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;
        try {
            assertEquals(2, executor.getCorePoolSize());
            assertEquals(4, executor.getMaxPoolSize());
            assertEquals(10, executor.getQueueCapacity());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void rejectsInvalidPoolSizesAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new AsyncConfig().taskExecutor(4, 2, 10));
    }
}
