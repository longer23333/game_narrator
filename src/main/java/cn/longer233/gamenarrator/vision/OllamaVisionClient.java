package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import cn.longer233.gamenarrator.ai.AiSettingsService;
import cn.longer233.gamenarrator.ai.AiContentRejectedException;
import cn.longer233.gamenarrator.media.SceneFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.IntConsumer;

@Component
public class OllamaVisionClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaVisionClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String model;
    private final String contentModel;
    private final int maxFrames;
    private final AdaptiveAiChatClient adaptiveChat;
    private final AiSettingsService aiSettings;
    private final FrameOcrService frameOcrService;
    private final int maximumSecondPassFrames;
    private final List<String> qualityFallbackModels;
    private final int qualityAttempts;
    private final double minimumConfidence;

    public OllamaVisionClient(ObjectMapper objectMapper, AdaptiveAiChatClient adaptiveChat,
            AiSettingsService aiSettings, FrameOcrService frameOcrService,
            @Value("${game-narrator.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${game-narrator.ollama.vision-model:qwen2.5vl:3b}") String model,
            @Value("${game-narrator.ollama.script-model:qwen2.5vl:3b}") String contentModel,
            @Value("${game-narrator.ollama.max-frames:0}") int maxFrames,
            @Value("${game-narrator.event-sampling.maximum-second-pass-frames:48}") int maximumSecondPassFrames,
            @Value("${game-narrator.vision-quality.fallback-models:}") String fallbackModels,
            @Value("${game-narrator.vision-quality.same-model-attempts:2}") int qualityAttempts,
            @Value("${game-narrator.vision-quality.minimum-confidence:0.45}") double minimumConfidence) {
        this.objectMapper = objectMapper;
        this.adaptiveChat = adaptiveChat;
        this.aiSettings = aiSettings;
        this.frameOcrService = frameOcrService;
        this.baseUri = URI.create(baseUrl);
        this.model = model;
        this.contentModel = contentModel;
        this.maxFrames = Math.max(0, maxFrames);
        this.maximumSecondPassFrames = Math.max(0, Math.min(200, maximumSecondPassFrames));
        this.qualityFallbackModels = Arrays.stream(Objects.toString(fallbackModels, "").split(","))
                .map(String::strip).filter(value -> !value.isBlank()).toList();
        this.qualityAttempts = Math.max(1, Math.min(5, qualityAttempts));
        this.minimumConfidence = Math.max(0, Math.min(1, minimumConfidence));
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public VideoUnderstandingResult analyze(Path manifestPath, String transcriptText) {
        return analyze(manifestPath, transcriptText, ignored -> { });
    }

    public VideoUnderstandingResult analyze(Path manifestPath, String transcriptText, IntConsumer progress) {
        try {
            List<Map<String, Object>> qualityAudit = new ArrayList<>();
            List<SceneFrame> allFrames = objectMapper.readerForListOf(SceneFrame.class).readValue(manifestPath.toFile());
            List<SceneFrame> selectedFrames = AdaptiveFrameSampler.sample(allFrames, maxFrames);
            if (selectedFrames.isEmpty()) throw new IllegalStateException("场景清单中没有可分析的截图");
            log.info("VIDEO_UNDERSTANDING_BEGIN model={} totalFrames={} selectedFrames={}", model, allFrames.size(), selectedFrames.size());
            List<FrameUnderstanding> analyses = new ArrayList<>();
            for (int index = 0; index < selectedFrames.size(); index++) {
                SceneFrame frame = selectedFrames.get(index);
                try {
                    analyses.add(frameOcrService.enrich(analyzeFrame(frame, transcriptText, qualityAudit, false,
                            "INITIAL")));
                } catch (AiContentRejectedException exception) {
                    log.warn("VIDEO_FRAME_CONTENT_REJECTED frame={} action=rule_fallback", frame.index());
                    analyses.add(frameOcrService.enrich(fallbackFrame(frame, transcriptText)));
                }
                progress.accept(10 + (int) Math.round((index + 1) * 80.0 / selectedFrames.size()));
            }
            if (VisionResultQuality.allZero(analyses)) {
                log.warn("VIDEO_VISION_ALL_ZERO action=quality_recovery frames={}", selectedFrames.size());
                analyses.clear();
                for (SceneFrame frame : selectedFrames) {
                    analyses.add(frameOcrService.enrich(analyzeFrame(frame, transcriptText, qualityAudit, true,
                            "ALL_ZERO_RECOVERY")));
                }
            }
            List<SceneFrame> secondPass = EventWindowSecondPass.select(allFrames, selectedFrames, analyses,
                    maximumSecondPassFrames);
            if (!secondPass.isEmpty()) {
                log.info("VIDEO_EVENT_SECOND_PASS_BEGIN candidateFrames={}", secondPass.size());
                for (SceneFrame frame : secondPass) {
                    try {
                        analyses.add(frameOcrService.enrich(analyzeFrame(frame, transcriptText, qualityAudit, false,
                                "EVENT_SECOND_PASS")));
                    } catch (AiContentRejectedException exception) {
                        analyses.add(frameOcrService.enrich(fallbackFrame(frame, transcriptText)));
                    }
                }
                analyses.sort(Comparator.comparingDouble(FrameUnderstanding::timestampSeconds));
                log.info("VIDEO_EVENT_SECOND_PASS_SUCCESS analyzedFrames={}", secondPass.size());
            }

            VideoContentAnalysis contentAnalysis;
            try {
                contentAnalysis = analyzeContent(transcriptText, analyses);
            } catch (Exception exception) {
                log.warn("VIDEO_CONTENT_ANALYSIS_FALLBACK reason={}", exception.getMessage());
                contentAnalysis = fallbackContentAnalysis(transcriptText, analyses);
            }
            String summary = formatSummary(contentAnalysis);
            progress.accept(95);
            Path output = manifestPath.getParent().resolve("visual-analysis.json");
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("model", model);
            document.put("contentModel", contentModel);
            document.put("summary", summary);
            document.put("contentAnalysis", contentAnalysis);
            document.put("transcriptText", transcriptText == null ? "" : transcriptText);
            document.put("frames", analyses);
            document.put("sampling", Map.of("firstPassFrames", selectedFrames.size(),
                    "secondPassFrames", secondPass.size(), "eventDriven", true));
            document.put("qualityRecovery", Map.of("minimumConfidence", minimumConfidence,
                    "attempts", List.copyOf(qualityAudit), "degradedFrames",
                    qualityAudit.stream().filter(item -> Boolean.TRUE.equals(item.get("degraded"))).count()));
            AtomicArtifactWriter.writeJson(objectMapper, output, document);
            log.info("VIDEO_UNDERSTANDING_SUCCESS analyzedFrames={} output={}", analyses.size(), output);
            return new VideoUnderstandingResult(summary, output.toString(), analyses);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("视频内容理解失败：" + exception.getMessage(), exception);
        }
    }

    public VideoUnderstandingResult analyzeWithoutAi(Path manifestPath, String transcriptText) {
        try {
            List<SceneFrame> allFrames = objectMapper.readerForListOf(SceneFrame.class).readValue(manifestPath.toFile());
            if (allFrames.isEmpty()) throw new IllegalStateException("场景清单为空");
            List<SceneFrame> firstPass = AdaptiveFrameSampler.sample(allFrames, maxFrames);
            List<FrameUnderstanding> analyses = new ArrayList<>(firstPass.stream()
                    .map(frame -> frameOcrService.enrich(fallbackFrame(frame, transcriptText))).toList());
            List<SceneFrame> secondPass = EventWindowSecondPass.select(allFrames, firstPass, analyses,
                    maximumSecondPassFrames);
            analyses.addAll(secondPass.stream()
                    .map(frame -> frameOcrService.enrich(fallbackFrame(frame, transcriptText))).toList());
            analyses.sort(Comparator.comparingDouble(FrameUnderstanding::timestampSeconds));
            VideoContentAnalysis content = fallbackContentAnalysis(transcriptText, analyses);
            String summary = formatSummary(content);
            Path output = manifestPath.getParent().resolve("visual-analysis.json");
            AtomicArtifactWriter.writeJson(objectMapper, output, Map.of(
                    "model", "RULE_BASED", "summary", summary, "contentAnalysis", content,
                    "transcriptText", transcriptText == null ? "" : transcriptText, "frames", analyses,
                    "sampling", Map.of("firstPassFrames", firstPass.size(), "secondPassFrames", secondPass.size(),
                            "eventDriven", true, "mode", "RULE_BASED")));
            return new VideoUnderstandingResult(summary, output.toString(), analyses);
        } catch (Exception exception) {
            throw new IllegalStateException("非 AI 场景分析失败：" + exception.getMessage(), exception);
        }
    }

    public boolean available() {
        if ("CLOUD".equals(aiSettings.current().mode()) && !"DEEPSEEK".equals(aiSettings.current().provider()))
            return !aiSettings.current().apiKey().isBlank();
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/tags")).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 && response.body().contains(model.split(":")[0]);
        } catch (Exception ignored) { return false; }
    }


    public String model() { return adaptiveChat.activeModel(true); }
    public URI baseUri() { return baseUri; }

    private FrameUnderstanding analyzeFrame(SceneFrame frame, String transcriptText,
            List<Map<String, Object>> qualityAudit, boolean requireNonZeroScore, String phase) throws Exception {
        String image = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(frame.imagePath())));
        String transcriptHint = abbreviate(transcriptText, 500);
        String prompt = """
                Also return confidenceScore from 0.0 to 1.0 for the reliability of this frame analysis.
                JSON 必须额外包含 ocrText 字段：填写截图中实际可见的界面文字，没有文字时返回空字符串。
                你是视频剪辑分析器。分析截图，严格返回 JSON 对象，不要 Markdown。
                字段：description（简体中文画面描述）、eventType（从探索/战斗/剧情/菜单/胜利/失败/其他选择）、
                excitementScore（0到100整数，代表适合作为高光片段的程度）。
                同期语音参考：%s
                """.formatted(transcriptHint);
        List<String> availableFallbacks = qualityFallbackModels.stream()
                .filter(candidate -> adaptiveChat == null || adaptiveChat.modelAvailable(candidate, true)).toList();
        VisionQualityRecovery.Result recovered = VisionQualityRecovery.recover(model, availableFallbacks,
                qualityAttempts, minimumConfidence, requireNonZeroScore,
                requestedModel -> adaptiveChat != null
                        ? adaptiveChat.chatJsonWithModel(prompt, List.of(image), true, requestedModel,
                                Duration.ofMinutes(5))
                        : chat(requestedModel, prompt, List.of(image), Duration.ofMinutes(5)));
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("frameIndex", frame.index());
        audit.put("phase", phase);
        audit.put("selectedModel", recovered.model());
        audit.put("degraded", recovered.degraded());
        audit.put("attempts", recovered.attempts());
        qualityAudit.add(audit);
        if (recovered.degraded()) {
            log.warn("VIDEO_FRAME_QUALITY_DEGRADED frame={} phase={} attempts={}", frame.index(), phase,
                    recovered.attempts().size());
            return fallbackFrame(frame, transcriptText);
        }
        JsonNode analysis = recovered.value();
        String raw = objectMapper.writeValueAsString(analysis);
        return new FrameUnderstanding(frame.index(), frame.timestampSeconds(), frame.imagePath(),
                analysis.path("description").asText("未识别出明确画面内容"),
                analysis.path("eventType").asText("其他"),
                Math.max(0, Math.min(100, analysis.path("excitementScore").asInt(0))),
                recovered.confidence(), analysis.path("ocrText").asText(""), raw);
    }

    private FrameUnderstanding fallbackFrame(SceneFrame frame, String transcriptText) {
        String hint = abbreviate(transcriptText, 120);
        String description = hint.isBlank() ? "该画面未经云端分析，已保留为普通剪辑候选镜头"
                : "同期语音：" + hint;
        return new FrameUnderstanding(frame.index(), frame.timestampSeconds(), frame.imagePath(),
                description, "其他", 35, "{\"fallback\":\"content_rejected\"}");
    }

    private VideoContentAnalysis analyzeContent(String transcriptText, List<FrameUnderstanding> frames) throws Exception {
        String frameDigest = frames.stream().map(frame -> "%.2f秒：%s（%s，高光分%d）".formatted(
                frame.timestampSeconds(), frame.description(), frame.eventType(), frame.excitementScore()))
                .reduce((left, right) -> left + "\n" + right).orElse("");
        String prompt = """
                你是专业中文视频内容策划。结合语音转写和按时间排列的画面分析，先判断整段视频讲了什么，
                再给出适合剪辑高光的时间点。必须使用简体中文，严格返回 JSON，不要 Markdown。
                JSON 字段：
                overview：2到4句完整内容概述；topics：主题关键词数组；tone：整体情绪和节奏；
                keyEvents：关键事件数组；highlightHints：数组，每项含 timestampSeconds、reason、importance（0到100）；
                highlightStrategy：一句具体剪辑建议。不要编造输入中不存在的事实。

                语音转写：
                %s

                画面时间线：
                %s
                """.formatted(abbreviate(transcriptText, 6000), frameDigest);
        JsonNode node = chat(contentModel, prompt, List.of(), Duration.ofMinutes(3));
        return objectMapper.treeToValue(node, VideoContentAnalysis.class);
    }

    private JsonNode chat(String chatModel, String prompt, List<String> images, Duration timeout) throws Exception {
        if (adaptiveChat != null) return adaptiveChat.chatJson(prompt, images, !images.isEmpty(), timeout);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        if (!images.isEmpty()) message.put("images", images);
        byte[] body = objectMapper.writeValueAsBytes(Map.of("model", chatModel, "stream", false,
                "format", "json", "messages", List.of(message), "options", Map.of("temperature", 0.1)));
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/chat")).timeout(timeout)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IllegalStateException("Ollama HTTP " + response.statusCode() + "：" + tail(response.body(), 1000));
        String content = objectMapper.readTree(response.body()).path("message").path("content").asText();
        return objectMapper.readTree(content);
    }

    private VideoContentAnalysis fallbackContentAnalysis(String transcriptText, List<FrameUnderstanding> analyses) {
        FrameUnderstanding highlight = analyses.stream().max(Comparator.comparingInt(FrameUnderstanding::excitementScore)).orElseThrow();
        Map<String, Long> counts = new LinkedHashMap<>();
        analyses.forEach(frame -> counts.merge(frame.eventType(), 1L, Long::sum));
        String overview = "视频语音主要内容：" + (abbreviate(transcriptText, 240).isBlank() ? "未检测到清晰语音" : abbreviate(transcriptText, 240))
                + "。画面以" + counts + "为主。";
        return new VideoContentAnalysis(overview, new ArrayList<>(counts.keySet()), "根据画面事件强度判断",
                List.of(highlight.description()), List.of(new HighlightHint(highlight.timestampSeconds(), highlight.description(), highlight.excitementScore())),
                "优先保留事件评分高且时间上分散的片段。");
    }

    private String formatSummary(VideoContentAnalysis analysis) {
        return "%s\n主题：%s；情绪与节奏：%s；高光建议：%s".formatted(
                Objects.toString(analysis.overview(), "未生成内容概述"),
                analysis.topics() == null ? "未识别" : String.join("、", analysis.topics()),
                Objects.toString(analysis.tone(), "未识别"), Objects.toString(analysis.highlightStrategy(), "按事件强度筛选"));
    }

    private String abbreviate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String tail(String value, int limit) { return value.length() <= limit ? value : value.substring(value.length() - limit); }
}
