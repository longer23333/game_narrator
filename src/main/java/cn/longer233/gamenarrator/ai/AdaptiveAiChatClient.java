package cn.longer233.gamenarrator.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class AdaptiveAiChatClient {
    private final ObjectMapper mapper;
    private final AiSettingsService settings;
    private final AiUsageService usage;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final URI ollama;
    private final String localVision;
    private final String localText;
    private final String cloudImagePolicy;
    private volatile long localVisionCheckedAt;
    private volatile boolean localVisionAvailable;

    public AdaptiveAiChatClient(ObjectMapper mapper, AiSettingsService settings, AiUsageService usage,
            @Value("${game-narrator.ollama.base-url:http://localhost:11434}") String ollama,
            @Value("${game-narrator.ollama.vision-model:qwen2.5vl:3b}") String localVision,
            @Value("${game-narrator.ollama.script-model:qwen2.5vl:3b}") String localText,
            @Value("${game-narrator.ai.cloud-image-policy:LOCAL_FIRST}") String cloudImagePolicy) {
        this.mapper=mapper; this.settings=settings; this.usage=usage; this.ollama=URI.create(ollama);
        this.localVision=localVision; this.localText=localText;
        this.cloudImagePolicy=cloudImagePolicy == null ? "LOCAL_FIRST" : cloudImagePolicy.trim().toUpperCase(Locale.ROOT);
    }

    public JsonNode chatJson(String prompt, List<String> images, boolean vision, Duration timeout) throws Exception {
        AiSettingsService.Settings value=settings.current();
        if ("LOCAL".equals(value.mode()) || (vision && "DEEPSEEK".equals(value.provider())))
            return ollama(prompt, images, vision ? localVision : localText, timeout);
        if (vision && "DASHSCOPE".equals(value.provider()) && "LOCAL_FIRST".equals(cloudImagePolicy)) {
            if (localVisionAvailable()) return ollama(prompt, images, localVision, timeout);
            throw new AiContentRejectedException("阿里云安全路由未上传原始截图；本地视觉模型不可用，已使用镜头规则");
        }
        if (value.apiKey().isBlank()) throw new IllegalStateException("请先在 AI 模型设置中填写云端 API Key");
        String model=vision ? value.visionModel() : value.textModel();
        String effectivePrompt = "DASHSCOPE".equals(value.provider()) ? minimizeCloudInput(prompt) : prompt;
        return switch(value.provider()) {
            case "ANTHROPIC" -> anthropic(effectivePrompt,images,model,value,timeout);
            case "GEMINI" -> gemini(effectivePrompt,images,model,value,timeout);
            default -> openAi(effectivePrompt, images, model, value, timeout);
        };
    }

    public String activeModel(boolean vision) {
        var value=settings.current();
        return ("LOCAL".equals(value.mode()) || (vision && "DEEPSEEK".equals(value.provider()))) ? (vision ? localVision : localText)
                : (vision ? value.visionModel() : value.textModel());
    }

    private JsonNode ollama(String prompt,List<String> images,String model,Duration timeout) throws Exception {
        Map<String,Object> message=new LinkedHashMap<>(); message.put("role","user"); message.put("content",prompt);
        if(!images.isEmpty()) message.put("images",images);
        byte[] body=mapper.writeValueAsBytes(Map.of("model",model,"stream",false,"format","json",
                "messages",List.of(message),"options",Map.of("temperature",0.2)));
        HttpRequest request=HttpRequest.newBuilder(ollama.resolve("/api/chat")).timeout(timeout)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()!=200) throw new IllegalStateException("本地模型 HTTP "+response.statusCode());
        JsonNode root=mapper.readTree(response.body());
        usage.record("LOCAL",model,root.path("prompt_eval_count").asLong(),root.path("eval_count").asLong(),0,0,0,0);
        return parseJson(root.path("message").path("content").asText());
    }

    private JsonNode openAi(String prompt,List<String> images,String model,AiSettingsService.Settings value,Duration timeout) throws Exception {
        Object content=prompt;
        if(!images.isEmpty()) {
            List<Map<String,Object>> parts=new ArrayList<>(); parts.add(Map.of("type","text","text",prompt));
            images.forEach(image->parts.add(Map.of("type","image_url","image_url",Map.of("url","data:image/jpeg;base64,"+image))));
            content=parts;
        }
        byte[] body=mapper.writeValueAsBytes(Map.of("model",model,"messages",List.of(Map.of("role","user","content",content)),
                "temperature",0.2,"response_format",Map.of("type","json_object")));
        String endpoint=openAiEndpoint(value.baseUrl());
        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                .header("Authorization","Bearer "+value.apiKey()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()/100!=2) {
            JsonNode error=mapper.readTree(response.body()).path("error");
            String detail=error.path("message").asText("").trim();
            if(detail.length()>240) detail=detail.substring(0,240);
            if (isContentRejected(response.body())) {
                throw new AiContentRejectedException("云端模型拒绝了该条内容，已改用本地规则继续处理");
            }
            throw new IllegalStateException("云端模型 HTTP "+response.statusCode()+(detail.isBlank()?"":"："+detail));
        }
        JsonNode root=mapper.readTree(response.body());
        JsonNode tokens=root.path("usage");
        record(value,model,tokens.path("prompt_tokens").asLong(),tokens.path("completion_tokens").asLong(),
                tokens.path("prompt_tokens_details").path("cached_tokens").asLong());
        return parseJson(root.path("choices").path(0).path("message").path("content").asText());
    }

    private JsonNode anthropic(String prompt,List<String> images,String model,AiSettingsService.Settings value,Duration timeout) throws Exception {
        List<Map<String,Object>> content=new ArrayList<>();
        content.add(Map.of("type","text","text",prompt));
        images.forEach(image->content.add(Map.of("type","image","source",Map.of("type","base64","media_type","image/jpeg","data",image))));
        byte[] body=mapper.writeValueAsBytes(Map.of("model",model,"max_tokens",4096,"messages",List.of(Map.of("role","user","content",content))));
        String endpoint=value.baseUrl().replaceAll("/+$","");
        if(!endpoint.endsWith("/messages")) endpoint+="/messages";
        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout).header("x-api-key",value.apiKey())
                .header("anthropic-version","2023-06-01").header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root=mapper.readTree(response.body());
        if(response.statusCode()/100!=2) throw providerError("Anthropic",response.statusCode(),root);
        JsonNode tokens=root.path("usage");
        record(value,model,tokens.path("input_tokens").asLong(),tokens.path("output_tokens").asLong(),
                tokens.path("cache_read_input_tokens").asLong());
        return parseJson(root.path("content").path(0).path("text").asText());
    }

    private JsonNode gemini(String prompt,List<String> images,String model,AiSettingsService.Settings value,Duration timeout) throws Exception {
        List<Map<String,Object>> parts=new ArrayList<>(); parts.add(Map.of("text",prompt));
        images.forEach(image->parts.add(Map.of("inlineData",Map.of("mimeType","image/jpeg","data",image))));
        byte[] body=mapper.writeValueAsBytes(Map.of("contents",List.of(Map.of("role","user","parts",parts)),
                "generationConfig",Map.of("responseMimeType","application/json")));
        String rootUrl=value.baseUrl().replaceAll("/+$","");
        String endpoint=rootUrl+"/models/"+java.net.URLEncoder.encode(model,StandardCharsets.UTF_8)+":generateContent";
        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout).header("x-goog-api-key",value.apiKey())
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root=mapper.readTree(response.body());
        if(response.statusCode()/100!=2) throw providerError("Gemini",response.statusCode(),root);
        JsonNode tokens=root.path("usageMetadata");
        record(value,model,tokens.path("promptTokenCount").asLong(),tokens.path("candidatesTokenCount").asLong(),
                tokens.path("cachedContentTokenCount").asLong());
        return parseJson(root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText());
    }

    private String openAiEndpoint(String baseUrl) {
        String value=baseUrl.trim();
        if(value.contains("/chat/completions")) return value;
        int query=value.indexOf('?');
        if(query<0) return value.replaceAll("/+$","")+"/chat/completions";
        return value.substring(0,query).replaceAll("/+$","")+"/chat/completions"+value.substring(query);
    }

    private IllegalStateException providerError(String provider,int status,JsonNode root) {
        String detail=root.path("error").path("message").asText(root.path("message").asText(""));
        if(detail.length()>240) detail=detail.substring(0,240);
        return new IllegalStateException(provider+" HTTP "+status+(detail.isBlank()?"":"："+detail));
    }

    private void record(AiSettingsService.Settings value,String model,long input,long output,long cached) {
        usage.record(value.provider(),model,input,output,cached,value.inputPricePerMillion(),
                value.outputPricePerMillion(),value.cachedInputPricePerMillion());
    }

    private JsonNode parseJson(String content) throws Exception {
        String cleaned=content.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
        return mapper.readTree(cleaned);
    }

    static boolean isContentRejected(String body) {
        String value = Objects.toString(body, "").toLowerCase(Locale.ROOT);
        return value.contains("inappropriate_content") || value.contains("inappropriate content")
                || value.contains("data_inspection_failed") || value.contains("content_filter")
                || value.contains("content moderation");
    }

    static String minimizeCloudInput(String prompt) {
        String value = Objects.toString(prompt, "")
                .replaceAll("(?s)(语音转写|语音参考)：.*?(?=\\n(?:画面时间线|高光片段|返回格式|字段)：)", "$1：[原始转写仅在本地保留]")
                .replaceAll("(?i)(authorization|api[_-]?key|cookie)\\s*[:=]\\s*\\S+", "$1=***")
                .replaceAll("https?://\\S+", "[链接已省略]")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .trim();
        return value.substring(0, Math.min(12_000, value.length()));
    }

    private boolean localVisionAvailable() {
        long now = System.currentTimeMillis();
        if (now - localVisionCheckedAt < 60_000) return localVisionAvailable;
        synchronized (this) {
            if (now - localVisionCheckedAt < 60_000) return localVisionAvailable;
            try {
                byte[] body = mapper.writeValueAsBytes(Map.of("model", localVision));
                HttpRequest request = HttpRequest.newBuilder(ollama.resolve("/api/show")).timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                localVisionAvailable = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
            } catch (Exception ignored) { localVisionAvailable = false; }
            localVisionCheckedAt = now;
            return localVisionAvailable;
        }
    }
}
