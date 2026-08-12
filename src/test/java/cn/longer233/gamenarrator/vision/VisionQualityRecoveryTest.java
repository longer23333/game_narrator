package cn.longer233.gamenarrator.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisionQualityRecoveryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void retriesPrimaryThenSwitchesModel() throws Exception {
        List<String> calls = new ArrayList<>();
        var result = VisionQualityRecovery.recover("primary", List.of("backup"), 2, .45, model -> {
            calls.add(model);
            if (calls.size() == 1) return mapper.readTree("{\"description\":\"\",\"confidenceScore\":0}");
            if (calls.size() == 2) throw new IllegalStateException("temporary failure");
            return mapper.readTree("""
                    {"description":"角色完成精准弹反","eventType":"战斗","excitementScore":90,"confidenceScore":0.93}
                    """);
        });

        assertThat(calls).containsExactly("primary", "primary", "backup");
        assertThat(result.degraded()).isFalse();
        assertThat(result.model()).isEqualTo("backup");
        assertThat(result.attempts()).extracting(VisionQualityRecovery.AttemptRecord::issues)
                .contains(List.of("EMPTY_DESCRIPTION", "LOW_CONFIDENCE"), List.of("REQUEST_FAILED"));
    }

    @Test
    void degradesWhenEveryModelProducesZeroDuringBatchRecovery() throws Exception {
        var result = VisionQualityRecovery.recover("primary", List.of("backup"), 2, .45, true,
                model -> mapper.readTree("""
                        {"description":"角色正在探索区域","eventType":"探索","excitementScore":0,"confidenceScore":0.9}
                        """));

        assertThat(result.degraded()).isTrue();
        assertThat(result.value()).isNull();
        assertThat(result.attempts()).hasSize(3)
                .allSatisfy(attempt -> assertThat(attempt.issues()).contains("ZERO_SCORE"));
    }
}
