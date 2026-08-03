package cn.longer233.gamenarrator.transcription;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TerminologyCorrector {
    public TranscriptionResult correct(TranscriptionResult result, String glossary) {
        Map<String, String> replacements = parse(glossary);
        if (replacements.isEmpty()) return result;
        String correctedText = replace(result.text(), replacements);
        rewrite(result.textPath(), replacements);
        rewrite(result.subtitlePath(), replacements);
        rewrite(result.detailJsonPath(), replacements);
        return new TranscriptionResult(correctedText, result.textPath(), result.subtitlePath(), result.detailJsonPath());
    }

    private Map<String, String> parse(String glossary) {
        Map<String, String> values = new LinkedHashMap<>();
        if (glossary == null) return values;
        glossary.lines().forEach(line -> {
            String[] pair = line.split("[=＝→]", 2);
            if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                values.put(pair[0].strip(), pair[1].strip());
            }
        });
        return values;
    }

    private void rewrite(String value, Map<String, String> replacements) {
        if (value == null || value.isBlank()) return;
        try {
            Path path = Path.of(value);
            if (Files.isRegularFile(path)) AtomicArtifactWriter.writeText(path,
                    replace(Files.readString(path, StandardCharsets.UTF_8), replacements), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("术语纠错文件写入失败：" + exception.getMessage(), exception);
        }
    }

    private String replace(String value, Map<String, String> replacements) {
        String corrected = value == null ? "" : value;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            corrected = corrected.replace(entry.getKey(), entry.getValue());
        }
        return corrected;
    }
}
