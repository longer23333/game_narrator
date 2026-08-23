package cn.longer233.gamenarrator.observability;

import cn.longer233.gamenarrator.common.TaskProcessRegistry;
import cn.longer233.gamenarrator.pipeline.EngineTaskContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/** Configures global admission limits and estimates a whole-pipeline task reservation. */
@Component
public class TaskResourceBudgetManager {
    private final int taskCpuUnits;
    private final long taskBaseMemoryBytes;
    private final long taskMaximumMemoryBytes;
    private final double sourceMemoryMultiplier;
    private final long localAiGpuMemoryBytes;

    public TaskResourceBudgetManager(
            @Value("${game-narrator.resources.cpu-units:${game-narrator.async.max-pool-size:4}}") int cpuUnits,
            @Value("${game-narrator.resources.memory-bytes:0}") long memoryBytes,
            @Value("${game-narrator.resources.gpu-memory-bytes:7516192768}") long gpuMemoryBytes,
            @Value("${game-narrator.resources.per-task.cpu-units:1}") int taskCpuUnits,
            @Value("${game-narrator.resources.per-task.base-memory-bytes:1073741824}") long taskBaseMemoryBytes,
            @Value("${game-narrator.resources.per-task.maximum-memory-bytes:4294967296}") long taskMaximumMemoryBytes,
            @Value("${game-narrator.resources.per-task.source-memory-multiplier:0.05}") double sourceMemoryMultiplier,
            @Value("${game-narrator.resources.per-task.local-ai-gpu-memory-bytes:4294967296}") long localAiGpuMemoryBytes) {
        long detectedMemory = Math.max(Math.max(0, taskMaximumMemoryBytes), Runtime.getRuntime().maxMemory() * 2);
        TaskProcessRegistry.configureResourceBudget(new TaskProcessRegistry.ResourceRequest(
                Math.max(1, cpuUnits), memoryBytes <= 0 ? detectedMemory : memoryBytes,
                gpuMemoryBytes <= 0 ? Long.MAX_VALUE : gpuMemoryBytes));
        this.taskCpuUnits = Math.max(1, taskCpuUnits);
        this.taskBaseMemoryBytes = Math.max(0, taskBaseMemoryBytes);
        this.taskMaximumMemoryBytes = Math.max(this.taskBaseMemoryBytes, taskMaximumMemoryBytes);
        if (!Double.isFinite(sourceMemoryMultiplier) || sourceMemoryMultiplier < 0) {
            throw new IllegalArgumentException("任务源文件内存倍率必须是非负有限数值");
        }
        this.sourceMemoryMultiplier = sourceMemoryMultiplier;
        this.localAiGpuMemoryBytes = Math.max(0, localAiGpuMemoryBytes);
    }

    public TaskProcessRegistry.ResourceRequest estimate(EngineTaskContext context) {
        long sourceBytes = 0;
        try { sourceBytes = Files.size(Path.of(context.sourceVideoPath())); }
        catch (Exception ignored) { }
        double estimatedSourceMemory = sourceBytes * sourceMemoryMultiplier;
        long sourceMemory = estimatedSourceMemory >= taskMaximumMemoryBytes
                ? taskMaximumMemoryBytes : Math.round(estimatedSourceMemory);
        long memory = sourceMemory >= taskMaximumMemoryBytes - taskBaseMemoryBytes
                ? taskMaximumMemoryBytes : taskBaseMemoryBytes + sourceMemory;
        boolean needsLocalAi = context.automaticGenerationEnabled()
                && (context.cloudVisionEnabled() || context.aiScriptEnabled() || context.aiVoiceEnabled());
        return new TaskProcessRegistry.ResourceRequest(taskCpuUnits, memory,
                needsLocalAi ? localAiGpuMemoryBytes : 0);
    }

    public TaskProcessRegistry.ResourceSnapshot snapshot() { return TaskProcessRegistry.resourceSnapshot(); }
}
