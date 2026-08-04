package cn.longer233.gamenarrator.observability;

import cn.longer233.gamenarrator.common.StorageCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StorageCapacityGuardTest {
    @TempDir Path temporary;

    @Test
    void acceptsTasksWhenConfiguredReserveIsAvailable() {
        StorageCapacityGuard guard = new StorageCapacityGuard(temporary.toString(), 0, 0,
                mock(StorageCleanupService.class));
        assertThat(guard.acceptingTasks()).isTrue();
        guard.requireTaskCapacity(1024);
    }

    @Test
    void cleansThenRejectsWhenReserveCannotBeSatisfied() {
        StorageCleanupService cleanup = mock(StorageCleanupService.class);
        StorageCapacityGuard guard = new StorageCapacityGuard(temporary.toString(), Long.MAX_VALUE, 0, cleanup);
        assertThatThrownBy(() -> guard.requireTaskCapacity(1))
                .isInstanceOf(InsufficientStorageException.class).hasMessageContaining("磁盘可用空间不足");
        verify(cleanup).cleanup();
    }
}
