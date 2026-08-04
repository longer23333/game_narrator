package cn.longer233.gamenarrator.task;

import cn.longer233.gamenarrator.diagnostics.SystemDiagnosticsService;
import cn.longer233.gamenarrator.pipeline.PendingTaskRecovery;
import cn.longer233.gamenarrator.pipeline.VideoTaskEngine;
import cn.longer233.gamenarrator.task.domain.CommentaryStyle;
import cn.longer233.gamenarrator.task.domain.VideoTask;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-lifecycle-integration",
        "spring.task.scheduling.enabled=false",
        "game-narrator.storage-root=./target/task-lifecycle-integration",
        "game-narrator.media-import.yt-dlp=./mvnw.cmd"
})
class TaskLifecycleIntegrationTest {
    @Autowired private VideoTaskRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private VideoTaskEngine engine;
    @MockBean private SystemDiagnosticsService diagnostics;

    @AfterEach
    void cleanDatabase() {
        repository.deleteAll();
        reset(engine, diagnostics);
    }

    @Test
    void staleTaskUpdateIsRejectedByDatabaseOptimisticLock() {
        VideoTask saved = repository.saveAndFlush(task("optimistic"));
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        VideoTask stale = transactions.execute(status -> repository.findById(saved.getId()).orElseThrow());

        transactions.executeWithoutResult(status -> {
            VideoTask current = repository.findById(saved.getId()).orElseThrow();
            current.rename("background-update");
            repository.saveAndFlush(current);
        });
        stale.rename("stale-editor-update");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> repository.saveAndFlush(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void startupRecoverySubmitsReadyAndProcessingTasksButNotCancelledTasks() throws Exception {
        VideoTask ready = repository.saveAndFlush(task("ready"));
        VideoTask processing = task("processing");
        processing.startIngestion();
        repository.saveAndFlush(processing);
        VideoTask cancelled = task("cancelled");
        cancelled.startIngestion();
        cancelled.cancel("集成测试取消");
        repository.saveAndFlush(cancelled);
        when(diagnostics.recoveryBlockers(any(VideoTask.class))).thenReturn(List.of());
        reset(engine);

        new PendingTaskRecovery(repository, engine, diagnostics)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(engine).start(ready.getId());
        verify(engine).start(processing.getId());
        verify(engine, never()).start(cancelled.getId());
        verifyNoMoreInteractions(engine);
    }

    private VideoTask task(String name) {
        return new VideoTask(name, "ACTION", CommentaryStyle.ANIME_THEATER,
                30, name + " integration test", name + ".mp4", false);
    }
}
