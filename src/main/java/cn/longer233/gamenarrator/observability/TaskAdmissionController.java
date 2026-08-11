package cn.longer233.gamenarrator.observability;

import cn.longer233.gamenarrator.common.TaskProcessRegistry;
import cn.longer233.gamenarrator.pipeline.EngineTaskContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Single admission boundary combining disk capacity and compute resource reservations. */
@Component
public class TaskAdmissionController {
    private final StorageCapacityGuard storage;
    private final TaskResourceBudgetManager resources;

    public TaskAdmissionController(StorageCapacityGuard storage, TaskResourceBudgetManager resources) {
        this.storage = storage;
        this.resources = resources;
    }

    public TaskProcessRegistry.ResourceLease admit(UUID taskId, int priority,
            EngineTaskContext context, long sourceBytes) {
        storage.requireTaskCapacity(sourceBytes);
        return TaskProcessRegistry.acquireResources(taskId, priority, resources.estimate(context));
    }

    public boolean acceptingTasks() {
        var snapshot = resources.snapshot();
        return storage.acceptingTasks()
                && snapshot.reservedCpuUnits() < snapshot.budget().cpuUnits()
                && snapshot.reservedMemoryBytes() < snapshot.budget().memoryBytes()
                && snapshot.reservedGpuMemoryBytes() < snapshot.budget().gpuMemoryBytes();
    }
}
