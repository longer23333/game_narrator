package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdaptiveAiChatRoutingTest {
    private final AiSettingsService settings = mock(AiSettingsService.class);
    private final OllamaChatBackend local = mock(OllamaChatBackend.class);
    private final CloudAiChatBackend cloud = mock(CloudAiChatBackend.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void routesLocalModeOnlyToOllamaAdapter() throws Exception {
        var value = configured("LOCAL", "DASHSCOPE");
        when(settings.current()).thenReturn(value);
        when(local.chatJson(anyString(), anyList(), eq("local-text"), eq(value), any()))
                .thenReturn(mapper.readTree("{\"ok\":true}"));
        var router = new AdaptiveAiChatClient(settings, local, cloud,
                "local-vision", "local-text", "CLOUD_ALLOWED");

        assertThat(router.chatJson("prompt", List.of(), false, Duration.ofSeconds(1)).path("ok").asBoolean()).isTrue();
        verifyNoInteractions(cloud);
    }

    @Test
    void routesCloudModeOnlyToCloudProtocolAdapter() throws Exception {
        var value = configured("CLOUD", "OPENAI");
        when(settings.current()).thenReturn(value);
        when(cloud.chatJson(anyString(), anyList(), eq("cloud-text"), eq(value), any()))
                .thenReturn(mapper.readTree("{\"ok\":true}"));
        var router = new AdaptiveAiChatClient(settings, local, cloud,
                "local-vision", "local-text", "CLOUD_ALLOWED");

        router.chatJson("prompt", List.of(), false, Duration.ofSeconds(1));
        verify(cloud).chatJson("prompt", List.of(), "cloud-text", value, Duration.ofSeconds(1));
        verifyNoInteractions(local);
    }

    private AiSettingsService.Settings configured(String mode, String provider) {
        return new AiSettingsService.Settings(mode, provider, "key", "https://example.invalid",
                "cloud-vision", "cloud-text", 0d, 0d, 0d);
    }
}
