package cn.longer233.gamenarrator.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class WhisperChunkSupport {
    private WhisperChunkSupport() { }

    static List<Chunk> plan(long durationMillis, long chunkMillis) {
        if (durationMillis <= 0 || chunkMillis <= 0) throw new IllegalArgumentException("音频时长和分段时长必须大于 0");
        List<Chunk> chunks = new ArrayList<>();
        for (long offset = 0; offset < durationMillis; offset += chunkMillis) {
            chunks.add(new Chunk(offset, Math.min(chunkMillis, durationMillis - offset), chunks.size() + 1));
        }
        return List.copyOf(chunks);
    }

    static void merge(ObjectMapper mapper, List<Path> prefixes, Path outputPrefix) throws Exception {
        List<String> texts = new ArrayList<>();
        List<String> subtitleBlocks = new ArrayList<>();
        ObjectNode mergedJson = mapper.createObjectNode();
        ArrayNode transcription = mergedJson.putArray("transcription");
        for (Path prefix : prefixes) {
            Path text = Path.of(prefix + ".txt");
            if (Files.isRegularFile(text)) {
                String value = Files.readString(text, StandardCharsets.UTF_8).trim();
                if (!value.isBlank()) texts.add(value);
            }
            Path subtitle = Path.of(prefix + ".srt");
            if (Files.isRegularFile(subtitle)) subtitleBlocks.addAll(srtBlocks(
                    Files.readString(subtitle, StandardCharsets.UTF_8)));
            Path json = Path.of(prefix + ".json");
            if (Files.isRegularFile(json)) {
                JsonNode root = mapper.readTree(json.toFile());
                if (mergedJson.size() == 1 && root.isObject()) {
                    root.fields().forEachRemaining(field -> {
                        if (!"transcription".equals(field.getKey())) mergedJson.set(field.getKey(), field.getValue());
                    });
                }
                root.path("transcription").forEach(item -> transcription.add(item.deepCopy()));
            }
        }
        Files.writeString(Path.of(outputPrefix + ".txt"), String.join(System.lineSeparator(), texts), StandardCharsets.UTF_8);
        StringBuilder srt = new StringBuilder();
        for (int index = 0; index < subtitleBlocks.size(); index++) {
            srt.append(index + 1).append(System.lineSeparator()).append(subtitleBlocks.get(index).trim())
                    .append(System.lineSeparator()).append(System.lineSeparator());
        }
        Files.writeString(Path.of(outputPrefix + ".srt"), srt.toString(), StandardCharsets.UTF_8);
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(outputPrefix + ".json").toFile(), mergedJson);
    }

    private static List<String> srtBlocks(String value) {
        List<String> blocks = new ArrayList<>();
        for (String raw : value.replace("\r\n", "\n").split("\n\s*\n")) {
            String block = raw.trim();
            if (block.isBlank()) continue;
            String[] lines = block.split("\n", 2);
            blocks.add(lines.length == 2 && lines[0].trim().matches("\\d+") ? lines[1] : block);
        }
        return blocks;
    }

    record Chunk(long offsetMillis, long durationMillis, int index) { }
}
