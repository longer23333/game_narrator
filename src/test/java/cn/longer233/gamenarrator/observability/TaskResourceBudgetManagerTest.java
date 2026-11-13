package cn.longer233.gamenarrator.observability;

import cn.longer233.gamenarrator.pipeline.EngineTaskContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskResourceBudgetManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void saturatesLargeFiniteSourceEstimateAtConfiguredMaximum() throws Exception {
        Path source = Files.write(temporaryDirectory.resolve("source.mp4"), new byte[] { 1 });
        TaskResourceBudgetManager manager = manager(Double.MAX_VALUE);
        EngineTaskContext context = mock(EngineTaskContext.class);
        when(context.sourceVideoPath()).thenReturn(source.toString());

        assertThat(manager.estimate(context).memoryBytes()).isEqualTo(100);
    }

    @Test
    void unreadableSourceFallsBackToBaseMemoryWithoutFailingTheTask() {
        TaskResourceBudgetManager manager = manager(0.05);
        EngineTaskContext missing = mock(EngineTaskContext.class);
        when(missing.sourceVideoPath()).thenReturn(temporaryDirectory.resolve("不存在的源文件.mp4").toString());

        // 读不到源文件时不能再抛异常打断任务，只能退化成基础内存预算。
        assertThat(manager.estimate(missing).memoryBytes()).isEqualTo(10);
        assertThat(manager.estimate(missing).gpuMemoryBytes()).isZero();

        EngineTaskContext nullPath = mock(EngineTaskContext.class);
        when(nullPath.sourceVideoPath()).thenReturn(null);
        assertThat(manager.estimate(nullPath).memoryBytes()).isEqualTo(10);
    }

    @Test
    void rejectsNonFiniteSourceMemoryMultiplier() {
        assertThatIllegalArgumentException().isThrownBy(() -> manager(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> manager(Double.POSITIVE_INFINITY));
    }

    private TaskResourceBudgetManager manager(double sourceMemoryMultiplier) {
        return new TaskResourceBudgetManager(4, 1_000, 1_000,
                1, 10, 100, sourceMemoryMultiplier, 0);
    }
}
