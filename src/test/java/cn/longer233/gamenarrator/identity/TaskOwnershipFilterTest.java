package cn.longer233.gamenarrator.identity;

import cn.longer233.gamenarrator.task.repository.VideoTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TaskOwnershipFilterTest {
    @Test void rejectsAnotherUsersTaskAcrossNestedTaskApis() throws Exception {
        UUID user=UUID.randomUUID(), task=UUID.randomUUID();
        VideoTaskRepository tasks=mock(VideoTaskRepository.class); CurrentUserContext current=mock(CurrentUserContext.class);
        when(current.userId()).thenReturn(user); when(tasks.existsByIdAndOwnerId(task,user)).thenReturn(false);
        var request=new MockHttpServletRequest("GET","/api/tasks/"+task+"/editor/revisions");
        var response=new MockHttpServletResponse(); var chain=new MockFilterChain();
        new TaskOwnershipFilter(tasks,current).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(404); assertThat(response.getContentAsString()).contains("TASK_NOT_FOUND");
    }
    @Test void recognizesSegmentPreviewTaskIdAndAllowsOwnedTask() throws Exception {
        UUID user=UUID.randomUUID(), task=UUID.randomUUID();
        VideoTaskRepository tasks=mock(VideoTaskRepository.class); CurrentUserContext current=mock(CurrentUserContext.class);
        when(current.userId()).thenReturn(user); when(tasks.existsByIdAndOwnerId(task,user)).thenReturn(true);
        var request=new MockHttpServletRequest("GET","/api/video-segments/"+task+"/clip");
        var response=new MockHttpServletResponse(); var chain=new MockFilterChain();
        new TaskOwnershipFilter(tasks,current).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(200); verify(tasks).existsByIdAndOwnerId(task,user);
    }
}
