package cn.longer233.gamenarrator.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.longer233.gamenarrator.ai.AdaptiveAiChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.time.Duration;

@Component
public class AiAssetTagger {
    private static final Pattern HAN = Pattern.compile("[\\p{IsHan}]");
    private final AdaptiveAiChatClient client;
    private final ObjectMapper objectMapper;

    public AiAssetTagger(ObjectMapper objectMapper, AdaptiveAiChatClient client) {
        this.objectMapper = objectMapper;
        this.client = client;
    }

    public List<String> classify(String assetType, String title, List<String> sourceTags) {
        try {
            String prompt = """
                    你是视频剪辑素材分类器。根据素材类型、标题和原始标签，从下面标准标签中选择1到5个：
                    战斗、冲击、转场、掠过、喜剧、尴尬、失败、胜利、悬疑、恐怖、治愈、
                    日常、热血、环境、循环、节奏、电子、管弦、角色反应、表情包、标题包装。
                    仅返回JSON：{"tags":["标签"]}。
                    素材类型：%s
                    标题：%s
                    原始标签：%s
                    """.formatted(assetType, title, String.join("、", sourceTags));
            JsonNode parsed = client.chatJson(prompt, List.of(), false, Duration.ofSeconds(20));
            List<String> tags = new ArrayList<>();
            parsed.path("tags").forEach(node -> {
                String value = normalize(node.asText());
                if (!value.isBlank()) tags.add(value);
            });
            return tags.isEmpty() ? fallback(title, sourceTags) : tags.stream().distinct().limit(5).toList();
        } catch (Exception exception) {
            return fallback(title, sourceTags);
        }
    }

    public List<AssetAiAnalysis> analyzeBatch(List<AssetAiInput> assets) {
        if (assets == null || assets.isEmpty()) return List.of();
        List<AssetAiInput> bounded = assets.stream().limit(20).toList();
        try {
            String prompt = """
                    你是中文视频剪辑素材分析器。请分析下面的素材，并严格返回 JSON。
                    要求：
                    1. 英文标题翻译成自然、简洁的中文标题；已有中文标题保持原意并适当润色。
                    2. 将英文标签翻译成中文标签。
                    3. 根据标题、类型和标签额外生成 3 到 6 个中文内容标签，覆盖场景、情绪、用途、动作或声音特征。
                    4. chineseTitle、translatedTags、analysisTags 中禁止出现纯英文结果；专有名词可保留但必须带中文说明。
                    5. 按输入顺序返回，不添加 Markdown。
                    返回格式：{"assets":[{"chineseTitle":"中文标题","translatedTags":["中文标签"],"analysisTags":["中文标签"]}]}
                    输入：%s
                    """.formatted(objectMapper.writeValueAsString(bounded));
            JsonNode rows = client.chatJson(prompt, List.of(), false, Duration.ofSeconds(30)).path("assets");
            List<AssetAiAnalysis> result = new ArrayList<>();
            for (int i = 0; i < bounded.size(); i++) {
                JsonNode row = rows.isArray() && i < rows.size() ? rows.get(i) : objectMapper.createObjectNode();
                String chineseTitle = chinese(row.path("chineseTitle").asText(), bounded.get(i).title());
                List<String> translated = chineseTags(row.path("translatedTags"));
                List<String> analysis = chineseTags(row.path("analysisTags"));
                if (analysis.isEmpty()) analysis = fallback(bounded.get(i).title(), bounded.get(i).sourceTags());
                result.add(new AssetAiAnalysis(chineseTitle, translated, analysis));
            }
            return result;
        } catch (Exception exception) {
            return bounded.stream().map(input -> new AssetAiAnalysis(
                    chinese("", input.title()), translateFallback(input.sourceTags()),
                    fallback(input.title(), input.sourceTags()))).toList();
        }
    }

    private List<String> chineseTags(JsonNode nodes) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (nodes != null) nodes.forEach(node -> {
            String value = normalize(node.asText());
            if (!value.isBlank() && HAN.matcher(value).find()) values.add(value);
        });
        return values.stream().limit(8).toList();
    }

    private String chinese(String generated, String original) {
        String value = generated == null ? "" : generated.trim();
        if (!value.isBlank() && HAN.matcher(value).find()) return value.substring(0, Math.min(500, value.length()));
        if (original != null && HAN.matcher(original).find()) return original.trim();
        return fallbackTitle(original);
    }

    private String fallbackTitle(String title) {
        String value = title == null ? "未命名素材" : title.trim();
        String lower = value.toLowerCase();
        if (lower.contains("green screen")) return "绿幕视频素材";
        if (lower.contains("sound effect")) return "音效素材";
        if (lower.contains("background music")) return "背景音乐素材";
        if (lower.contains("funny") || lower.contains("reaction")) return "搞笑反应素材";
        return value.isBlank() ? "未命名素材" : value.substring(0, Math.min(500, value.length()));
    }

    private List<String> translateFallback(List<String> sourceTags) {
        LinkedHashSet<String> translated = new LinkedHashSet<>();
        String text = String.join(" ", sourceTags == null ? List.of() : sourceTags).toLowerCase();
        match(translated, text, "欢快", "happy", "upbeat", "cheerful");
        match(translated, text, "搞笑", "funny", "comedy", "reaction");
        match(translated, text, "背景音乐", "background", "music");
        match(translated, text, "音效", "sound", "sfx");
        match(translated, text, "绿幕", "green screen", "chroma key");
        match(translated, text, "游戏", "game", "gaming");
        return translated.stream().limit(8).toList();
    }

    public record AssetAiInput(String title, String assetType, List<String> sourceTags) { }
    public record AssetAiAnalysis(String chineseTitle, List<String> translatedTags, List<String> analysisTags) { }

    /** Fast, network-free classification for interactive catalog searches. */
    public List<String> classifyFast(String assetType, String title, List<String> sourceTags) {
        return fallback(title, sourceTags);
    }

    private List<String> fallback(String title, List<String> sourceTags) {
        String text = (title + " " + String.join(" ", sourceTags)).toLowerCase();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        match(result, text, "冲击", "impact", "hit", "boom", "explosion");
        match(result, text, "转场", "whoosh", "swoosh", "transition");
        match(result, text, "喜剧", "funny", "comedy", "laugh");
        match(result, text, "恐怖", "horror", "suspense", "dark");
        match(result, text, "战斗", "battle", "fight", "combat");
        match(result, text, "环境", "ambient", "nature", "atmosphere");
        match(result, text, "循环", "loop");
        return result.stream().limit(5).toList();
    }

    private void match(LinkedHashSet<String> result, String text, String label, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                result.add(label);
                return;
            }
        }
    }

    private String normalize(String value) {
        return value.trim().replaceAll("[#，,;；]", "").substring(0, Math.min(100,
                value.trim().replaceAll("[#，,;；]", "").length()));
    }
}
