package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.task.domain.TaskStatus;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class PendingTaskRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PendingTaskRecovery.class);
    private final VideoTaskRepository repository;
    private final VideoTaskEngine engine;

    public PendingTaskRecovery(VideoTaskRepository repository, VideoTaskEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    @Override
    public void run(ApplicationArguments args) {
        var readyTasks = repository.findByStatusIn(
                java.util.List.of(TaskStatus.READY, TaskStatus.PROCESSING));
        log.info("ENGINE_RECOVERY_SCAN recoverableTaskCount={}", readyTasks.size());
        readyTasks.forEach(task -> {
            log.info("ENGINE_RECOVERY_SUBMIT taskId={} name={}", task.getId(), task.getName());
            engine.start(task.getId());
        });
    }
}
