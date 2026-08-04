package cn.longer233.gamenarrator.transcription;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubtitleChunkAnalysisService {
    private final ObjectMapper mapper;
    private final int maxCharacters;

    public SubtitleChunkAnalysisService(ObjectMapper mapper,
            @Value("${game-narrator.subtitle.chunk-characters:4000}") int maxCharacters) {
        this.mapper = mapper;
        this.maxCharacters = Math.max(500, maxCharacters);
    }

    public Path analyze(Path subtitlePath) {
        if (subtitlePath == null || !Files.isRegularFile(subtitlePath)) return null;
        try {
            List<Cue> cues = parse(Files.readString(subtitlePath, StandardCharsets.UTF_8));
            if (cues.isEmpty()) return null;
            List<List<Cue>> chunks = chunk(cues);
            List<Map<String, Object>> output = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                List<Cue> chunk = chunks.get(index);
                String text = chunk.stream().map(Cue::text).reduce((a, b) -> a + " " + b).orElse("");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", index + 1);
                item.put("outline", outline(text));
                item.put("timeline", Map.of("startSeconds", chunk.getFirst().start(),
                        "endSeconds", chunk.getLast().end()));
                item.put("excitementScore", excitement(text));
                item.put("text", text);
                output.add(item);
            }
            Path target = subtitlePath.resolveSibling(subtitlePath.getFileName() + ".analysis.json");
            AtomicArtifactWriter.writeText(target, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(Map.of("strategy", "chunked-subtitle-v1", "chunks", output)),
                    StandardCharsets.UTF_8);
            return target;
        } catch (Exception exception) {
            throw new IllegalStateException("字幕分块分析失败：" + exception.getMessage(), exception);
        }
    }

    private List<List<Cue>> chunk(List<Cue> cues) {
        List<List<Cue>> result = new ArrayList<>();
        List<Cue> current = new ArrayList<>();
        int length = 0;
        for (Cue cue : cues) {
            if (!current.isEmpty() && length + cue.text().length() > maxCharacters) {
                result.add(List.copyOf(current)); current.clear(); length = 0;
            }
            current.add(cue); length += cue.text().length();
        }
        if (!current.isEmpty()) result.add(List.copyOf(current));
        return result;
    }

    private String outline(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(180, normalized.length()));
    }

    private int excitement(String text) {
        int score = 35;
        String lower = text.toLowerCase();
        for (String keyword : List.of("击杀", "胜利", "反杀", "精彩", "危险", "boss", "win", "kill", "!", "！")) {
            int from = 0;
            while ((from = lower.indexOf(keyword, from)) >= 0) { score += 8; from += keyword.length(); }
        }
        return Math.min(100, score);
    }

    private List<Cue> parse(String srt) {
        List<Cue> cues = new ArrayList<>();
        for (String block : srt.replace("\r\n", "\n").split("\n\\s*\n")) {
            String[] lines = block.lines().toArray(String[]::new);
            int timing = -1;
            for (int i = 0; i < lines.length; i++) if (lines[i].contains("-->")) { timing = i; break; }
            if (timing < 0) continue;
            String[] range = lines[timing].split("-->");
            if (range.length != 2) continue;
            String text = String.join(" ", java.util.Arrays.copyOfRange(lines, timing + 1, lines.length)).trim();
            if (!text.isBlank()) cues.add(new Cue(seconds(range[0]), seconds(range[1]), text));
        }
        return cues;
    }

    private double seconds(String timestamp) {
        String[] parts = timestamp.trim().replace(',', '.').split(":");
        return Double.parseDouble(parts[0]) * 3600 + Double.parseDouble(parts[1]) * 60
                + Double.parseDouble(parts[2]);
    }

    private record Cue(double start, double end, String text) {}
}
