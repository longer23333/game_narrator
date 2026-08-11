package cn.longer233.gamenarrator.task.web;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.task.application.VideoTaskService;
import cn.longer233.gamenarrator.task.application.VideoTaskView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskEventStreamServiceTest {
    @Test
    void deltaContainsOnlyChangedAndRemovedTasks() {
        UUID unchangedId = UUID.randomUUID();
        UUID changedId = UUID.randomUUID();
        UUID removedId = UUID.randomUUID();
        VideoTaskView unchanged = task(unchangedId);
        VideoTaskView before = task(changedId);
        VideoTaskView after = task(changedId);
        VideoTaskView removed = task(removedId);

        TaskStreamMessage delta = TaskEventStreamService.diff(
                Map.of(unchangedId, unchanged, changedId, before, removedId, removed),
                Map.of(unchangedId, unchanged, changedId, after), 123L);

        assertThat(delta.type()).isEqualTo("delta");
        assertThat(delta.tasks()).containsExactly(after);
        assertThat(delta.removedIds()).containsExactly(removedId);
    }

    @Test
    void scheduledPublishingKeepsTheOwnerCapturedAtSubscriptionTime() {
        UUID owner = UUID.randomUUID();
        VideoTaskService tasks = mock(VideoTaskService.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        when(currentUser.userId()).thenReturn(owner);
        when(tasks.findAllForOwner(owner)).thenReturn(List.of());
        TaskEventStreamService service = new TaskEventStreamService(tasks, currentUser);

        service.subscribe();
        service.publishChanges();

        verify(tasks, atLeast(2)).findAllForOwner(owner);
    }

    private VideoTaskView task(UUID id) {
        VideoTaskView task = mock(VideoTaskView.class);
        when(task.id()).thenReturn(id);
        return task;
    }
}
