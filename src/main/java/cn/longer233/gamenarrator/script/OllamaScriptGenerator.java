package cn.longer233.gamenarrator.script;

import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import cn.longer233.gamenarrator.ai.AiContentRejectedException;
import cn.longer233.gamenarrator.highlight.HighlightClip;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class OllamaScriptGenerator {
    private static final Logger log = LoggerFactory.getLogger(OllamaScriptGenerator.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final String model;
    private final AdaptiveAiChatClient adaptiveChat;

    @Autowired
    public OllamaScriptGenerator(ObjectMapper objectMapper, AdaptiveAiChatClient adaptiveChat,
            @Value("${game-narrator.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${game-narrator.ollama.script-model:${game-narrator.ollama.vision-model:qwen2.5vl:3b}}") String model) {
        this.objectMapper = objectMapper;
        this.adaptiveChat = adaptiveChat;
        this.baseUri = URI.create(baseUrl);
        this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    OllamaScriptGenerator(ObjectMapper objectMapper, String baseUrl, String model) {
        this.objectMapper = objectMapper;
        this.adaptiveChat = null;
        this.baseUri = URI.create(baseUrl);
        this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public GeneratedScript generate(Path highlightPath, String category, String style,
                                    String taskBrief, String transcript) {
        if (adaptiveChat != null) return generateAdaptive(highlightPath, category, style, taskBrief, transcript);
        try {
            JsonNode highlightDocument = objectMapper.readTree(highlightPath.toFile());
            List<HighlightClip> clips = objectMapper.readerForListOf(HighlightClip.class)
                    .readValue(highlightDocument.path("clips"));
            if (clips.isEmpty()) throw new IllegalStateException("高光清单中没有可写作文案的片段");
            String prompt = buildPrompt(clips, category, style, taskBrief, transcript);
            log.info("SCRIPT_GENERATION_BEGIN model={} clipCount={} promptChars={}", model, clips.size(), prompt.length());
            Map<String, Object> requestBody = Map.of(
                    "model", model, "stream", false, "format", "json",
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "options", Map.of("temperature", 0.65));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/chat"))
                    .timeout(Duration.ofMinutes(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(requestBody))).build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama 文案请求返回 HTTP " + response.statusCode());
            }
            String content = objectMapper.readTree(response.body()).path("message").path("content").asText();
            JsonNode generated = objectMapper.readTree(content);
            String title = generated.path("title").asText("游戏高光剧场");
            String synopsis = generated.path("synopsis").asText("围绕高光镜头生成的解说剧场");
            List<ScriptSegment> segments = alignSegments(generated.path("segments"), clips);
            String fullNarration = String.join("\n", segments.stream().map(ScriptSegment::narration).toList());
            Path output = highlightPath.getParent().resolve("generated-script.json");
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("model", model);
            document.put("title", title);
            document.put("synopsis", synopsis);
            document.put("fullNarration", fullNarration);
            document.put("segments", segments);
            document.put("qualityReview", qualityReview(generated, segments));
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper, output, document);
            log.info("SCRIPT_GENERATION_SUCCESS model={} segmentCount={} narrationChars={} output={}",
                    model, segments.size(), fullNarration.length(), output);
            return new GeneratedScript(title, synopsis, fullNarration, output.toString(), segments);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("AI 文案生成失败：" + exception.getMessage(), exception);
        }
    }

    public GeneratedScript generateWithoutAi(Path highlightPath) {
        try {
            List<HighlightClip> clips = objectMapper.readerForListOf(HighlightClip.class)
                    .readValue(objectMapper.readTree(highlightPath.toFile()).path("clips"));
            if (clips.isEmpty()) throw new IllegalStateException("高光清单为空");
            List<ScriptSegment> segments = new ArrayList<>();
            for (int index = 0; index < clips.size(); index++) {
                HighlightClip clip = clips.get(index);
                segments.add(new ScriptSegment(index + 1, clip.startSeconds(), clip.endSeconds(), "", "", ""));
            }
            Path output = highlightPath.getParent().resolve("generated-script.json");
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper, output, Map.of(
                    "model", "MANUAL", "title", "手动剪辑", "synopsis", "未启用 AI 文案",
                    "fullNarration", "", "segments", segments));
            return new GeneratedScript("手动剪辑", "未启用 AI 文案", "", output.toString(), segments);
        } catch (Exception exception) {
            throw new IllegalStateException("创建手动文案轨道失败：" + exception.getMessage(), exception);
        }
    }

    public ScriptSegment regenerateSegment(ScriptSegment current, String instruction,
                                           String previousNarration, String nextNarration) {
        if (adaptiveChat != null) return regenerateAdaptive(current, instruction, previousNarration, nextNarration);
        try {
            String prompt = """
                    请重写一个中文游戏解说片段，只返回 JSON，不要输出 Markdown。
                    保持原有事实、人物关系和时间范围，不模仿任何具名创作者。
                    narration、subtitle、effectCue 三个字段必须全部使用简体中文，不得返回纯英文内容。
                    上一段解说：%s
                    当前片段：%s
                    下一段解说：%s
                    用户要求：%s
                    返回格式：{"narration":"25到55个中文字符","subtitle":"简洁中文字幕","effectCue":"中文特效建议"}
                    """.formatted(
                    previousNarration == null ? "" : previousNarration,
                    objectMapper.writeValueAsString(current),
                    nextNarration == null ? "" : nextNarration,
                    instruction == null ? "" : instruction);
            Map<String, Object> requestBody = Map.of(
                    "model", model, "stream", false, "format", "json",
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "options", Map.of("temperature", 0.7));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/chat"))
                    .timeout(Duration.ofMinutes(3)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(requestBody))).build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama segment request returned HTTP " + response.statusCode());
            }
            JsonNode generated = objectMapper.readTree(
                    objectMapper.readTree(response.body()).path("message").path("content").asText());
            String narration = generated.path("narration").asText().trim();
            if (narration.isBlank()) {
                throw new IllegalStateException("Generated narration is empty");
            }
            if (!containsChinese(narration)) {
                throw new IllegalStateException("AI 重写没有返回中文解说，请调整要求后重试");
            }
            String subtitle = generated.path("subtitle").asText(narration).trim();
            String effectCue = generated.path("effectCue").asText(current.effectCue()).trim();
            if (!containsChinese(subtitle)) subtitle = narration;
            if (!containsChinese(effectCue)) effectCue = containsChinese(current.effectCue())
                    ? current.effectCue() : "节奏转场";
            return new ScriptSegment(current.clipIndex(), current.startSeconds(), current.endSeconds(),
                    narration, subtitle.isBlank() ? narration : subtitle,
                    effectCue.isBlank() ? current.effectCue() : effectCue);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Script segment regeneration failed: " + exception.getMessage(), exception);
        }
    }

    private GeneratedScript generateAdaptive(Path highlightPath, String category, String style,
                                             String taskBrief, String transcript) {
        try {
            JsonNode highlightDocument = objectMapper.readTree(highlightPath.toFile());
            List<HighlightClip> clips = objectMapper.readerForListOf(HighlightClip.class)
                    .readValue(highlightDocument.path("clips"));
            if (clips.isEmpty()) throw new IllegalStateException("高光清单中没有可生成文案的片段");
            JsonNode generated = adaptiveChat.chatJson(
                    buildPrompt(clips, category, style, taskBrief, transcript), List.of(), false, Duration.ofMinutes(5));
            String title = generated.path("title").asText("游戏高光剧场");
            String synopsis = generated.path("synopsis").asText("根据高光镜头生成的解说剧场");
            List<ScriptSegment> segments = alignSegments(generated.path("segments"), clips);
            String fullNarration = String.join("\n", segments.stream().map(ScriptSegment::narration).toList());
            Path output = highlightPath.getParent().resolve("generated-script.json");
            Map<String,Object> document = new LinkedHashMap<>();
            document.put("model", adaptiveChat.activeModel(false));
            document.put("title", title); document.put("synopsis", synopsis);
            document.put("fullNarration", fullNarration); document.put("segments", segments);
            document.put("qualityReview", generated.path("qualityReview"));
            cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper, output, document);
            return new GeneratedScript(title, synopsis, fullNarration, output.toString(), segments);
        } catch (AiContentRejectedException exception) {
            try {
                List<HighlightClip> clips = objectMapper.readerForListOf(HighlightClip.class)
                        .readValue(objectMapper.readTree(highlightPath.toFile()).path("clips"));
                List<ScriptSegment> segments = alignSegments(objectMapper.createArrayNode(), clips);
                String narration = String.join("\n", segments.stream().map(ScriptSegment::narration).toList());
                Path output = highlightPath.getParent().resolve("generated-script.json");
                cn.longer233.gamenarrator.common.AtomicArtifactWriter.writeJson(objectMapper, output, Map.of(
                        "model", "RULE_FALLBACK", "title", "游戏剪辑", "synopsis", "云端审核拒绝单次请求，已使用本地通用文案",
                        "fullNarration", narration, "segments", segments));
                return new GeneratedScript("游戏剪辑", "已使用本地通用文案", narration, output.toString(), segments);
            } catch (Exception fallbackFailure) { throw new IllegalStateException("本地文案降级失败", fallbackFailure); }
        } catch (IllegalStateException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("AI 文案生成失败：" + exception.getMessage(), exception); }
    }

    private ScriptSegment regenerateAdaptive(ScriptSegment current, String instruction,
                                              String previousNarration, String nextNarration) {
        try {
            String prompt = "请重写中文游戏解说片段，只返回 JSON，字段为 narration、subtitle、effectCue。"
                    + "\n上一段：" + Objects.toString(previousNarration, "")
                    + "\n当前段：" + objectMapper.writeValueAsString(current)
                    + "\n下一段：" + Objects.toString(nextNarration, "")
                    + "\n用户要求：" + Objects.toString(instruction, "");
            JsonNode generated = adaptiveChat.chatJson(prompt, List.of(), false, Duration.ofMinutes(3));
            String narration = generated.path("narration").asText().trim();
            if (narration.isBlank()) throw new IllegalStateException("AI 返回的解说为空");
            String subtitle = generated.path("subtitle").asText(narration).trim();
            String effectCue = generated.path("effectCue").asText(current.effectCue()).trim();
            return new ScriptSegment(current.clipIndex(), current.startSeconds(), current.endSeconds(), narration,
                    subtitle.isBlank() ? narration : subtitle, effectCue.isBlank() ? current.effectCue() : effectCue);
        } catch (IllegalStateException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("AI 片段重写失败：" + exception.getMessage(), exception); }
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    List<ScriptSegment> alignSegments(JsonNode generatedSegments, List<HighlightClip> clips) {
        List<ScriptSegment> result = new ArrayList<>();
        for (int index = 0; index < clips.size(); index++) {
            HighlightClip clip = clips.get(index);
            JsonNode node = generatedSegments.isArray() && index < generatedSegments.size()
                    ? generatedSegments.get(index) : objectMapper.createObjectNode();
            String narration = node.path("narration").asText();
            if (narration.isBlank()) narration = "镜头切换，故事在这一刻继续推进。";
            String subtitle = node.path("subtitle").asText();
            if (subtitle.isBlank()) subtitle = narration;
            String effectCue = node.path("effectCue").asText();
            if (effectCue.isBlank()) effectCue = "节奏转场";
            result.add(new ScriptSegment(index + 1, clip.startSeconds(), clip.endSeconds(),
                    narration, subtitle, effectCue));
        }
        return result;
    }

    Map<String, Object> qualityReview(JsonNode generated, List<ScriptSegment> segments) {
        JsonNode review = generated.path("qualityReview");
        List<String> issues = new ArrayList<>();
        review.path("issues").forEach(issue -> {
            String value = issue.asText().trim();
            if (!value.isBlank() && issues.size() < 8) issues.add(value);
        });
        if (segments.stream().anyMatch(segment -> segment.narration().isBlank()
                || segment.subtitle().isBlank() || segment.effectCue().isBlank())) {
            issues.add("存在空白文案、字幕或特效提示");
        }
        int score = Math.max(0, Math.min(100, review.path("score").asInt(issues.isEmpty() ? 85 : 65)));
        return Map.of(
                "model", model,
                "score", score,
                "passed", review.path("passed").asBoolean(score >= 70 && issues.isEmpty()),
                "issues", issues,
                "summary", review.path("summary").asText(issues.isEmpty() ? "文案结构完整" : "文案需要人工复核")
        );
    }

    private String buildPrompt(List<HighlightClip> clips, String category, String style,
                               String brief, String transcript) throws Exception {
        String transcriptHint = transcript == null ? "" : transcript.substring(0, Math.min(900, transcript.length()));
        return """
                你是原创游戏视频剧场编剧。根据高光片段写一份中文解说文案，只返回 JSON，不使用 Markdown。
                内容类别：%s
                风格：%s（ANIME_THEATER 表示日式动漫剧场感，但不得模仿具体作者的独特措辞）
                创作要求：%s
                原视频语音参考（可能有识别错误，只用于理解内容，严禁逐句复制）：%s
                高光片段：%s
                JSON 格式：{"title":"原创标题","synopsis":"剧情概述","segments":[{"clipIndex":1,"narration":"适合配音的台词","subtitle":"精简字幕","effectCue":"建议的转场或屏幕特效"}]}
                segments 数量必须与高光片段完全一致并保持原顺序。每段台词约 25 到 55 个汉字，必须重新创作、口语自然、前后连贯、有起承转合；不得照抄语音参考，不虚构具体角色姓名。subtitle 和 effectCue 均不得为空。
                同时返回 qualityReview：{"score":0-100,"passed":true或false,"issues":["具体问题"],"summary":"简短结论"}，检查连贯性、事实一致性、可配音性和字幕精炼度。
                """.formatted(category, style, brief, transcriptHint, objectMapper.writeValueAsString(clips));
    }
}
