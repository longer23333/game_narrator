package cn.longer233.gamenarrator.transcription;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Produces timestamp-preserving, evidence-labelled speaker segments from Whisper detail JSON. */
@Component
public class SpeakerDiarizationService {
    private static final Pattern PLAYER = Pattern.compile("(?i)(我|咱|兄弟|家人|观众|直播|这波|操作|手柄|键盘|鼠标|I\\b|we\\b|chat\\b|stream)");
    private static final Pattern CHARACTER = Pattern.compile("(?i)(旁白|系统|任务|警告|殿下|勇士|旅行者|指挥官|dialogue|narrator|system|npc|character)");
    private final ObjectMapper mapper;

    public SpeakerDiarizationService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Path analyze(TranscriptionResult result) {
        if (result == null || result.detailJsonPath() == null || result.detailJsonPath().isBlank()) return null;
        Path detail = Path.of(result.detailJsonPath());
        if (!Files.isRegularFile(detail)) return null;
        try {
            JsonNode root = mapper.readTree(detail.toFile());
            List<Segment> segments = readSegments(root.path("transcription"));
            if (segments.isEmpty()) return null;
            markOverlaps(segments);
            Path output = detail.resolveSibling("speaker-segments.json");
            ObjectNode document = mapper.createObjectNode();
            document.put("schemaVersion", 1);
            document.put("method", "native-speaker-labels-with-explainable-fallback");
            document.put("requiresReview", segments.stream().anyMatch(segment -> "TEXT_FALLBACK".equals(segment.evidence)));
            ArrayNode values = document.putArray("segments");
            for (Segment segment : segments) values.add(toJson(segment));
            AtomicArtifactWriter.writeJson(mapper, output, document);

            if (root instanceof ObjectNode object) {
                ObjectNode summary = object.putObject("speakerDiarization");
                summary.put("path", output.toString());
                summary.put("segmentCount", segments.size());
                summary.put("method", document.path("method").asText());
                summary.put("requiresReview", document.path("requiresReview").asBoolean());
                AtomicArtifactWriter.writeJson(mapper, detail, root);
            }
            return output;
        } catch (Exception exception) {
            throw new IllegalStateException("说话人分段生成失败：" + exception.getMessage(), exception);
        }
    }

    private List<Segment> readSegments(JsonNode source) {
        List<Segment> result = new ArrayList<>();
        if (!source.isArray()) return result;
        int index = 0;
        for (JsonNode node : source) {
            long start = millis(node, true);
            long end = Math.max(start, millis(node, false));
            String text = text(node);
            String nativeSpeaker = firstText(node, "speaker", "speaker_id", "speakerId", "voice");
            Classification classification = classify(nativeSpeaker, text);
            result.add(new Segment(index++, start, end, text, classification.category,
                    nativeSpeaker, classification.confidence, classification.evidence));
        }
        result.sort(Comparator.comparingLong(segment -> segment.startMillis));
        return result;
    }

    private void markOverlaps(List<Segment> segments) {
        for (int left = 0; left < segments.size(); left++) {
            for (int right = left + 1; right < segments.size(); right++) {
                Segment a = segments.get(left);
                Segment b = segments.get(right);
                if (b.startMillis >= a.endMillis) break;
                if (!sameNativeSpeaker(a, b)) {
                    segments.set(left, a.asMultiSpeaker());
                    segments.set(right, b.asMultiSpeaker());
                    a = segments.get(left);
                }
            }
        }
    }

    private boolean sameNativeSpeaker(Segment a, Segment b) {
        return a.nativeSpeaker != null && b.nativeSpeaker != null
                && a.nativeSpeaker.equalsIgnoreCase(b.nativeSpeaker);
    }

    private Classification classify(String speaker, String text) {
        if (speaker != null) {
            String normalized = speaker.toLowerCase(Locale.ROOT);
            if (normalized.contains("multi") || normalized.contains("overlap") || normalized.contains("多人"))
                return new Classification("MULTI_SPEAKER", 0.98, "NATIVE_LABEL");
            if (normalized.contains("player") || normalized.contains("streamer") || normalized.contains("玩家"))
                return new Classification("PLAYER_VOICE", 0.96, "NATIVE_LABEL");
            if (normalized.contains("npc") || normalized.contains("narrator") || normalized.contains("character")
                    || normalized.contains("system") || normalized.contains("角色") || normalized.contains("旁白"))
                return new Classification("GAME_CHARACTER_NARRATOR", 0.96, "NATIVE_LABEL");
        }
        if (CHARACTER.matcher(text).find()) return new Classification("GAME_CHARACTER_NARRATOR", 0.66, "TEXT_FALLBACK");
        if (PLAYER.matcher(text).find()) return new Classification("PLAYER_VOICE", 0.62, "TEXT_FALLBACK");
        return new Classification("GAME_CHARACTER_NARRATOR", 0.40, "TEXT_FALLBACK");
    }

    private ObjectNode toJson(Segment segment) {
        ObjectNode node = mapper.createObjectNode();
        node.put("index", segment.index);
        node.put("startMillis", segment.startMillis);
        node.put("endMillis", segment.endMillis);
        node.put("speakerType", segment.category);
        node.put("confidence", segment.confidence);
        node.put("evidence", segment.evidence);
        if (segment.nativeSpeaker != null) node.put("nativeSpeaker", segment.nativeSpeaker);
        node.put("text", segment.text);
        return node;
    }

    private long millis(JsonNode node, boolean start) {
        String key = start ? "from" : "to";
        JsonNode offset = node.path("offsets").path(key);
        if (offset.isNumber()) return offset.asLong();
        JsonNode seconds = node.path(start ? "start" : "end");
        if (seconds.isNumber()) return Math.round(seconds.asDouble() * 1000d);
        return parseTimestamp(node.path("timestamps").path(key).asText(""));
    }

    private long parseTimestamp(String value) {
        try {
            String[] parts = value.replace(',', '.').split(":");
            if (parts.length != 3) return 0;
            return Math.round((Integer.parseInt(parts[0]) * 3600d + Integer.parseInt(parts[1]) * 60d
                    + Double.parseDouble(parts[2])) * 1000d);
        } catch (RuntimeException ignored) { return 0; }
    }

    private String text(JsonNode node) {
        return firstText(node, "text", "sentence", "content", "transcript") == null ? ""
                : firstText(node, "text", "sentence", "content", "transcript");
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("").strip();
            if (!value.isBlank()) return value;
        }
        return null;
    }

    private record Classification(String category, double confidence, String evidence) { }
    private record Segment(int index, long startMillis, long endMillis, String text, String category,
                           String nativeSpeaker, double confidence, String evidence) {
        Segment asMultiSpeaker() {
            return new Segment(index, startMillis, endMillis, text, "MULTI_SPEAKER", nativeSpeaker, 0.92, "TIMELINE_OVERLAP");
        }
    }
}
