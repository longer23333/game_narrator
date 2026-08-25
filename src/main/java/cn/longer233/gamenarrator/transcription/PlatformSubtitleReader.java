package cn.longer233.gamenarrator.transcription;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class PlatformSubtitleReader {
    private static final Pattern SEQUENCE_LINE = Pattern.compile("\\d+");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    public TranscriptionResult read(Path sourceVideo) {
        Path subtitle = sourceVideo.resolveSibling(sourceVideo.getFileName() + ".platform.srt");
        if (!Files.isRegularFile(subtitle)) return null;
        try {
            String srt = Files.readString(subtitle, StandardCharsets.UTF_8);
            String text = String.join(" ", srt.lines()
                    .filter(line -> !line.isBlank())
                    .filter(line -> !SEQUENCE_LINE.matcher(line.trim()).matches())
                    .filter(line -> !line.contains("-->"))
                    .map(line -> HTML_TAG.matcher(line).replaceAll("").trim())
                    .filter(line -> !line.isBlank())
                    .toList());
            if (text.isBlank()) return null;
            Path textPath = sourceVideo.resolveSibling(sourceVideo.getFileName() + ".platform.txt");
            AtomicArtifactWriter.writeText(textPath, text, StandardCharsets.UTF_8);
            return new TranscriptionResult(text, textPath.toString(), subtitle.toString(), null);
        } catch (Exception exception) {
            return null;
        }
    }
}
