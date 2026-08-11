package cn.longer233.gamenarrator.diagnostics;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import cn.longer233.gamenarrator.task.application.TaskNotFoundException;
import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosticsControllerTest {
    private final SystemDiagnosticsService diagnostics = mock(SystemDiagnosticsService.class);
    private final DiagnosticLogService logs = mock(DiagnosticLogService.class);
    private final VideoTaskRepository tasks = mock(VideoTaskRepository.class);
    private final CurrentUserContext current = mock(CurrentUserContext.class);
    private final DiagnosticsController controller = new DiagnosticsController(diagnostics, logs, tasks, current);

    @Test
    void rejectsTaskLogOwnedByAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(current.userId()).thenReturn(userId);
        when(current.authenticated()).thenReturn(true);
        when(tasks.existsByIdAndOwnerId(taskId, userId)).thenReturn(false);

        assertThatThrownBy(() -> controller.logs(400, taskId))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void limitsGlobalLogsToAuthenticatedUsersOwnTasks() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(current.userId()).thenReturn(userId);
        when(current.authenticated()).thenReturn(true);
        when(current.role()).thenReturn("USER");
        when(tasks.findIdsByOwnerIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(taskId));
        when(logs.recentForTasks(List.of(taskId), 300)).thenReturn("owned logs");

        assertThat(controller.logs(300, null)).isEqualTo("owned logs");
        verify(logs).recentForTasks(List.of(taskId), 300);
    }

    @Test
    void allowsAdministratorToInspectRequestedTask() {
        UUID taskId = UUID.randomUUID();
        when(current.authenticated()).thenReturn(true);
        when(current.role()).thenReturn("ADMIN");
        when(logs.recentForTask(taskId, 200)).thenReturn("task logs");

        assertThat(controller.logs(200, taskId)).isEqualTo("task logs");
        verify(logs).recentForTask(taskId, 200);
    }
}
