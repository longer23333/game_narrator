package cn.longer233.gamenarrator.transcription;

import cn.longer233.gamenarrator.common.AtomicArtifactWriter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PlatformSubtitleReader {
    public TranscriptionResult read(Path sourceVideo) {
        Path subtitle = sourceVideo.resolveSibling(sourceVideo.getFileName() + ".platform.srt");
        if (!Files.isRegularFile(subtitle)) return null;
        try {
            String srt = Files.readString(subtitle, StandardCharsets.UTF_8);
            String text = srt.lines()
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.trim().matches("\\d+"))
                    .filter(line -> !line.contains("-->"))
                    .map(line -> line.replaceAll("<[^>]+>", "").trim())
                    .filter(line -> !line.isBlank())
                    .reduce((left, right) -> left + " " + right).orElse("");
            if (text.isBlank()) return null;
            Path textPath = sourceVideo.resolveSibling(sourceVideo.getFileName() + ".platform.txt");
            AtomicArtifactWriter.writeText(textPath, text, StandardCharsets.UTF_8);
            return new TranscriptionResult(text, textPath.toString(), subtitle.toString(), null);
        } catch (Exception exception) {
            return null;
        }
    }
}
