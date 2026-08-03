package cn.longer233.gamenarrator.ai;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveAiChatClientTest {
    @Test
    void recognizesDashScopeContentModerationResponses() {
        assertThat(AdaptiveAiChatClient.isContentRejected("Input data may contain inappropriate content"))
                .isTrue();
        assertThat(AdaptiveAiChatClient.isContentRejected("{\"code\":\"inappropriate_content\"}"))
                .isTrue();
        assertThat(AdaptiveAiChatClient.isContentRejected("rate limit exceeded")).isFalse();
    }

    @Test
    void minimizesRawTranscriptAndCredentialsBeforeDashScopeTextRequests() {
        String minimized = AdaptiveAiChatClient.minimizeCloudInput("""
                语音转写：这里是未经处理的长转写 Authorization: Bearer-secret
                画面时间线：10秒：角色进入场景
                """);
        assertThat(minimized).contains("原始转写仅在本地保留", "10秒：角色进入场景")
                .doesNotContain("未经处理的长转写", "Bearer-secret");
    }
}
