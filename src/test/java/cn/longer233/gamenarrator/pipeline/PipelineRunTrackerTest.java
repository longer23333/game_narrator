package cn.longer233.gamenarrator.pipeline;

import cn.longer233.gamenarrator.identity.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineRunTrackerTest {
    @Test
    void serializationFailureFallsBackToEmptySnapshotInsteadOfPropagating() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        PipelineRunTracker tracker = new PipelineRunTracker(mock(JdbcTemplate.class), mapper,
                mock(CurrentUserContext.class));

        // json() 是私有工具方法，这里直接验证失败时仍返回可落库的空对象，且不再静默。
        Object result = ReflectionTestUtils.invokeMethod(tracker, "json", Map.of("taskId", "x"));

        assertThat(result).isEqualTo("{}");
    }

    @Test
    void serializesNormallyWhenMapperWorks() {
        PipelineRunTracker tracker = new PipelineRunTracker(mock(JdbcTemplate.class), new ObjectMapper(),
                mock(CurrentUserContext.class));

        Object result = ReflectionTestUtils.invokeMethod(tracker, "json", Map.of("taskId", "x"));

        assertThat(result).isEqualTo("{\"taskId\":\"x\"}");
    }
}
