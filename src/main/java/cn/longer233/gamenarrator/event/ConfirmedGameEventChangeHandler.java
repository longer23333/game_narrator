package cn.longer233.gamenarrator.event;

import cn.longer233.gamenarrator.task.application.VideoTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ConfirmedGameEventChangeHandler {
    private static final Logger log = LoggerFactory.getLogger(ConfirmedGameEventChangeHandler.class);
    private final VideoTaskService tasks;

    public ConfirmedGameEventChangeHandler(VideoTaskService tasks) {
        this.tasks = tasks;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void regenerate(ConfirmedGameEventChanged event) {
        log.info("CONFIRMED_EVENT_PIPELINE_REGENERATION taskId={} eventId={}", event.taskId(), event.eventId());
        tasks.regenerateAfterConfirmedEventChange(event.taskId());
    }
}
