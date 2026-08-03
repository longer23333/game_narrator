package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class FailedVisionTaskRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FailedVisionTaskRepair.class);
    private final VideoTaskRepository repository;

    public FailedVisionTaskRepair(VideoTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var failedTasks = repository.findByStatus(TaskStatus.FAILED);
        int repaired = 0;
        for (var task : failedTasks) {
            if (task.canDeferFailedVideoUnderstanding()
                    && isUnavailableDependencyFailure(task.getFailureReason())) {
                String previousFailure = task.getFailureReason();
                task.deferVideoUnderstanding(
                        "等待本地视觉模型 qwen2.5vl:3b；请执行 .\\scripts\\setup-vision-model.ps1");
                repaired++;
                log.info("VISION_TASK_REPAIRED taskId={} previousFailure={}",
                        task.getId(), previousFailure);
            }
        }
        log.info("VISION_TASK_REPAIR_SCAN failedTaskCount={} repairedTaskCount={}",
                failedTasks.size(), repaired);
    }

    private boolean isUnavailableDependencyFailure(String reason) {
        if (reason == null) return false;
        return reason.contains("ClosedChannelException")
                || reason.contains("ConnectException")
                || reason.contains("Ollama")
                || reason.contains("视觉模型未就绪");
    }
}
