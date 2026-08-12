package cn.longer233.gamenarrator.transcription;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import cn.longer233.gamenarrator.event.BossBattleKnowledgePack;
import cn.longer233.gamenarrator.event.GameKnowledgePackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TerminologyCorrector {
    private static final Pattern SRT_TIMELINE = Pattern.compile(
            "^\\s*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->\\s+\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}.*$");
    private static final List<String> JSON_TEXT_FIELDS = List.of("text", "sentence", "content", "transcript");
    private final GameKnowledgePackService knowledgePacks;
    private final ObjectMapper objectMapper;

    public TerminologyCorrector(GameKnowledgePackService knowledgePacks, ObjectMapper objectMapper) {
        this.knowledgePacks = knowledgePacks;
        this.objectMapper = objectMapper;
    }

    public TranscriptionResult correct(TranscriptionResult result, String userGlossary) {
        List<Replacement> replacements = replacements(userGlossary);
        if (replacements.isEmpty()) return result;
        String correctedText = replaceText(result.text(), replacements);
        rewritePlainText(result.textPath(), replacements);
        rewriteSubtitle(result.subtitlePath(), replacements);
        rewriteDetailJson(result.detailJsonPath(), replacements);
        return new TranscriptionResult(correctedText, result.textPath(), result.subtitlePath(), result.detailJsonPath());
    }

    List<Replacement> replacements(String userGlossary) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (BossBattleKnowledgePack.TerminologyEntry entry : knowledgePacks.activeTerminology()) {
            if (entry == null || entry.canonical() == null || entry.aliases() == null) continue;
            for (String alias : entry.aliases()) add(merged, alias, entry.canonical(), false);
        }
        parseUserGlossary(userGlossary).forEach((alias, canonical) -> add(merged, alias, canonical, true));
        return merged.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .map(entry -> new Replacement(entry.getKey(), entry.getValue())).toList();
    }

    private Map<String, String> parseUserGlossary(String glossary) {
        Map<String, String> values = new LinkedHashMap<>();
        if (glossary == null) return values;
        glossary.lines().forEach(line -> {
            String value = line.strip();
            if (value.isBlank() || value.startsWith("#")) return;
            String[] pair = value.split("[=＝→]", 2);
            if (pair.length == 2) add(values, pair[0], pair[1], true);
        });
        return values;
    }

    private void add(Map<String, String> values, String alias, String canonical, boolean overwrite) {
        String from = alias == null ? "" : alias.strip();
        String to = canonical == null ? "" : canonical.strip();
        if (from.isBlank() || to.isBlank() || from.equals(to)) return;
        if (overwrite) values.put(from, to); else values.putIfAbsent(from, to);
    }

    private void rewritePlainText(String value, List<Replacement> replacements) {
        rewrite(value, content -> replaceText(content, replacements));
    }

    private void rewriteSubtitle(String value, List<Replacement> replacements) {
        rewrite(value, content -> {
            String[] lines = content.split("(?<=\\n)", -1);
            StringBuilder corrected = new StringBuilder(content.length());
            for (String line : lines) {
                String body = line.endsWith("\r\n") ? line.substring(0, line.length() - 2)
                        : line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
                String ending = line.substring(body.length());
                if (body.isBlank() || body.strip().matches("\\d+") || SRT_TIMELINE.matcher(body).matches())
                    corrected.append(body);
                else corrected.append(replaceText(body, replacements));
                corrected.append(ending);
            }
            return corrected.toString();
        });
    }

    private void rewriteDetailJson(String value, List<Replacement> replacements) {
        if (value == null || value.isBlank()) return;
        try {
            Path path = Path.of(value);
            if (!Files.isRegularFile(path)) return;
            JsonNode root = objectMapper.readTree(path.toFile());
            correctJsonText(root, replacements);
            AtomicArtifactWriter.writeJson(objectMapper, path, root);
        } catch (Exception exception) {
            throw new IllegalStateException("术语纠错 JSON 写入失败：" + exception.getMessage(), exception);
        }
    }

    private void correctJsonText(JsonNode node, List<Replacement> replacements) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                if (child != null && child.isTextual() && JSON_TEXT_FIELDS.contains(name))
                    object.put(name, replaceText(child.asText(), replacements));
                else correctJsonText(child, replacements);
            }
        } else if (node.isArray()) node.forEach(child -> correctJsonText(child, replacements));
    }

    private void rewrite(String value, java.util.function.UnaryOperator<String> correction) {
        if (value == null || value.isBlank()) return;
        try {
            Path path = Path.of(value);
            if (Files.isRegularFile(path)) AtomicArtifactWriter.writeText(path,
                    correction.apply(Files.readString(path, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("术语纠错文件写入失败：" + exception.getMessage(), exception);
        }
    }

    private String replaceText(String value, List<Replacement> replacements) {
        String corrected = value == null ? "" : value;
        for (Replacement replacement : replacements)
            corrected = corrected.replace(replacement.alias(), replacement.canonical());
        return corrected;
    }

    record Replacement(String alias, String canonical) { }
}
