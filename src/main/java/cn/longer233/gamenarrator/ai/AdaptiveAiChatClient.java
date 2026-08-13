package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Selects a local or cloud protocol adapter; it contains no provider inference implementation. */
@Component
public class AdaptiveAiChatClient {
    private final AiSettingsService settings;
    private final OllamaChatBackend local;
    private final CloudAiChatBackend cloud;
    private final String localVision;
    private final String localText;
    private final String cloudImagePolicy;

    public AdaptiveAiChatClient(AiSettingsService settings, OllamaChatBackend local, CloudAiChatBackend cloud,
            @Value("${game-narrator.ollama.vision-model:qwen2.5vl:3b}") String localVision,
            @Value("${game-narrator.ollama.script-model:qwen2.5vl:3b}") String localText,
            @Value("${game-narrator.ai.cloud-image-policy:LOCAL_FIRST}") String cloudImagePolicy) {
        this.settings = settings;
        this.local = local;
        this.cloud = cloud;
        this.localVision = localVision;
        this.localText = localText;
        this.cloudImagePolicy = Objects.toString(cloudImagePolicy, "LOCAL_FIRST").trim().toUpperCase(Locale.ROOT);
    }

    public JsonNode chatJson(String prompt, List<String> images, boolean vision, Duration timeout) throws Exception {
        var value = settings.current();
        if (useLocal(value, vision)) return local.chatJson(prompt, images, localModel(value, vision), value, timeout);
        if (vision && "DASHSCOPE".equals(value.provider()) && "LOCAL_FIRST".equals(cloudImagePolicy)) {
            if (local.modelAvailable(localVision)) return local.chatJson(prompt, images, localVision, value, timeout);
            throw new AiContentRejectedException("阿里云安全路由未上传原始截图；本地视觉模型不可用，已使用镜头规则");
        }
        String safePrompt = "DASHSCOPE".equals(value.provider()) ? minimizeCloudInput(prompt) : prompt;
        return cloud.chatJson(safePrompt, images, vision ? value.visionModel() : value.textModel(), value, timeout);
    }

    public JsonNode chatJsonWithModel(String prompt, List<String> images, boolean vision,
                                      String requestedModel, Duration timeout) throws Exception {
        var value = settings.current();
        String model = requestedModel == null || requestedModel.isBlank()
                ? (vision ? activeModel(true) : activeModel(false)) : requestedModel.trim();
        if (useLocal(value, vision)) return local.chatJson(prompt, images, model, value, timeout);
        if (vision && "DASHSCOPE".equals(value.provider()) && "LOCAL_FIRST".equals(cloudImagePolicy)) {
            if (local.modelAvailable(model)) return local.chatJson(prompt, images, model, value, timeout);
            throw new AiContentRejectedException("Requested local vision model is unavailable: " + model);
        }
        String safePrompt = "DASHSCOPE".equals(value.provider()) ? minimizeCloudInput(prompt) : prompt;
        return cloud.chatJson(safePrompt, images, model, value, timeout);
    }

    public boolean modelAvailable(String model, boolean vision) {
        var value = settings.current();
        if (useLocal(value, vision) || (vision && "DASHSCOPE".equals(value.provider())
                && "LOCAL_FIRST".equals(cloudImagePolicy))) return local.modelAvailable(model);
        return !value.apiKey().isBlank();
    }

    public String activeModel(boolean vision) {
        var value = settings.current();
        return useLocal(value, vision) ? localModel(value, vision) : (vision ? value.visionModel() : value.textModel());
    }

    private boolean useLocal(AiSettingsService.Settings value, boolean vision) {
        return "LOCAL".equals(value.mode()) || (vision && "DEEPSEEK".equals(value.provider()));
    }
    private String localModel(AiSettingsService.Settings value, boolean vision) {
        String selected = vision ? value.visionModel() : value.textModel();
        if ("LOCAL".equals(value.mode()) && selected != null && !selected.isBlank()) return selected;
        return vision ? localVision : localText;
    }

    static boolean retryableStatus(int status) { return status == 408 || status == 429 || status >= 500; }
    static boolean isContentRejected(String body) {
        String value = Objects.toString(body, "").toLowerCase(Locale.ROOT);
        return value.contains("inappropriate_content") || value.contains("inappropriate content")
                || value.contains("data_inspection_failed") || value.contains("content_filter")
                || value.contains("content moderation");
    }
    static String minimizeCloudInput(String prompt) {
        String value = Objects.toString(prompt, "")
                .replaceAll("(?s)(语音转写|语音参考)[:：].*?(?=\\n(?:画面时间线|高光片段|返回格式|字段)[:：])",
                        "$1：[原始转写仅在本地保留]")
                .replaceAll("(?i)(authorization|api[_-]?key|cookie)\\s*[:=]\\s*\\S+", "$1=***")
                .replaceAll("https?://\\S+", "[链接已省略]")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return value.substring(0, Math.min(12_000, value.length()));
    }
}
