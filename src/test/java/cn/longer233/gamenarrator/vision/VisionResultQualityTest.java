package cn.longer233.gamenarrator.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisionResultQualityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsEmptyAndLowConfidenceResults() throws Exception {
        var quality = VisionResultQuality.assess(mapper.readTree("""
                {"description":"","eventType":"OTHER","excitementScore":0,"confidenceScore":0.1}
                """), .45);

        assertThat(quality.acceptable()).isFalse();
        assertThat(quality.issues()).containsExactly("EMPTY_DESCRIPTION", "LOW_CONFIDENCE");
    }

    @Test
    void acceptsDetailedResultUsingInferredConfidence() throws Exception {
        var quality = VisionResultQuality.assess(mapper.readTree("""
                {"description":"角色成功弹反敌人的致命攻击","eventType":"战斗","excitementScore":88}
                """), .45);

        assertThat(quality.acceptable()).isTrue();
        assertThat(quality.confidence()).isGreaterThanOrEqualTo(.8);
    }

    @Test
    void inferredConfidenceNeverDecidesWhetherResultIsRejected() throws Exception {
        // 模型没给 confidenceScore：推断值再低也不能当成“置信度不足”把结果判死
        var quality = VisionResultQuality.assess(mapper.readTree("""
                {"description":"弹反成功","eventType":"战斗","excitementScore":0}
                """), .45);

        assertThat(quality.confidenceKnown()).isFalse();
        assertThat(quality.issues()).doesNotContain("LOW_CONFIDENCE");
        assertThat(quality.acceptable()).isTrue();
    }

    @Test
    void declaredLowConfidenceStillRejects() throws Exception {
        var quality = VisionResultQuality.assess(mapper.readTree("""
                {"description":"角色成功弹反敌人的致命攻击","eventType":"战斗","excitementScore":88,"confidenceScore":0.2}
                """), .45);

        assertThat(quality.confidenceKnown()).isTrue();
        assertThat(quality.issues()).containsExactly("LOW_CONFIDENCE");
        assertThat(quality.acceptable()).isFalse();
    }

    @Test
    void zeroScoreCanBeRequiredForBatchRecovery() throws Exception {
        var quality = VisionResultQuality.assess(mapper.readTree("""
                {"description":"角色正在探索地图中的新区域","eventType":"探索","excitementScore":0,"confidenceScore":0.9}
                """), .45, true);

        assertThat(quality.issues()).containsExactly("ZERO_SCORE");
        assertThat(VisionResultQuality.allZero(List.of(
                new FrameUnderstanding(1, 1, "a", "d", "e", 0, "{}"),
                new FrameUnderstanding(2, 2, "b", "d", "e", 0, "{}")))).isTrue();
    }
}
