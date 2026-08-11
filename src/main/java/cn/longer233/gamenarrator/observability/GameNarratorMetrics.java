package cn.longer233.gamenarrator.observability;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GameNarratorMetrics {
    private final AtomicLong gpuMemoryUsedBytes = new AtomicLong(-1);
    private final AtomicLong gpuMemoryTotalBytes = new AtomicLong(-1);

    public GameNarratorMetrics(MeterRegistry registry, VideoTaskRepository tasks,
            StorageCapacityGuard capacity, TaskResourceBudgetManager resources, TaskAdmissionController admission,
            cn.longer233.gamenarrator.pipeline.PrioritizedTaskExecutor pipelines,
            @Qualifier("taskExecutor") Executor executor) {
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        Gauge.builder("game_narrator_task_queue_size", pool, item -> item.getThreadPoolExecutor().getQueue().size())
                .description("Queued asynchronous work items").register(registry);
        Gauge.builder("game_narrator_task_executor_active", pool, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active asynchronous task workers").register(registry);
        for (TaskStatus status : TaskStatus.values()) Gauge.builder("game_narrator_tasks", tasks,
                repository -> repository.countByStatus(status))
                .tag("status", status.name()).register(registry);
        Gauge.builder("game_narrator_storage_usable_bytes", capacity, StorageCapacityGuard::usableBytes).register(registry);
        Gauge.builder("game_narrator_storage_total_bytes", capacity, StorageCapacityGuard::totalBytes).register(registry);
        Gauge.builder("game_narrator_accepting_tasks", capacity, value -> value.acceptingTasks() ? 1 : 0).register(registry);
        Gauge.builder("game_narrator_admission_accepting_tasks", admission,
                value -> value.acceptingTasks() ? 1 : 0).register(registry);
        Gauge.builder("game_narrator_pipeline_queue_size", pipelines,
                cn.longer233.gamenarrator.pipeline.PrioritizedTaskExecutor::queueSize).register(registry);
        Gauge.builder("game_narrator_pipeline_active", pipelines,
                cn.longer233.gamenarrator.pipeline.PrioritizedTaskExecutor::activeCount).register(registry);
        Gauge.builder("game_narrator_resource_waiting_tasks", resources,
                value -> value.snapshot().waitingTasks()).register(registry);
        Gauge.builder("game_narrator_resource_active_tasks", resources,
                value -> value.snapshot().activeTasks()).register(registry);
        Gauge.builder("game_narrator_resource_reserved_cpu_units", resources,
                value -> value.snapshot().reservedCpuUnits()).register(registry);
        Gauge.builder("game_narrator_resource_reserved_memory_bytes", resources,
                value -> value.snapshot().reservedMemoryBytes()).register(registry);
        Gauge.builder("game_narrator_resource_reserved_gpu_memory_bytes", resources,
                value -> value.snapshot().reservedGpuMemoryBytes()).register(registry);
        Gauge.builder("game_narrator_resource_budget_cpu_units", resources,
                value -> value.snapshot().budget().cpuUnits()).register(registry);
        Gauge.builder("game_narrator_resource_budget_memory_bytes", resources,
                value -> value.snapshot().budget().memoryBytes()).register(registry);
        Gauge.builder("game_narrator_resource_budget_gpu_memory_bytes", resources,
                value -> value.snapshot().budget().gpuMemoryBytes()).register(registry);
        Gauge.builder("game_narrator_gpu_memory_used_bytes", gpuMemoryUsedBytes, AtomicLong::get).register(registry);
        Gauge.builder("game_narrator_gpu_memory_total_bytes", gpuMemoryTotalBytes, AtomicLong::get).register(registry);
        ExternalProcessRunner.activeCounts().forEach((type, ignored) -> Gauge.builder(
                "game_narrator_external_processes_active", type,
                key -> ExternalProcessRunner.activeCounts().getOrDefault(key, 0)).tag("type", type).register(registry));
    }

    @Scheduled(fixedDelayString = "${game-narrator.observability.gpu-refresh-ms:15000}")
    public void refreshGpu() {
        try {
            var result = ExternalProcessRunner.run(List.of("nvidia-smi", "--query-gpu=memory.used,memory.total",
                    "--format=csv,noheader,nounits"), Duration.ofSeconds(3));
            String line = result.output().lines().findFirst().orElse("");
            if (result.exitCode() == 0 && !line.isBlank()) {
                String[] values = line.split(",");
                gpuMemoryUsedBytes.set(Long.parseLong(values[0].trim()) * 1024 * 1024);
                gpuMemoryTotalBytes.set(Long.parseLong(values[1].trim()) * 1024 * 1024);
            }
        } catch (Exception ignored) {
            gpuMemoryUsedBytes.set(-1); gpuMemoryTotalBytes.set(-1);
        }
    }
}
