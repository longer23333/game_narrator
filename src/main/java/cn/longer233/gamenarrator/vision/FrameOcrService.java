package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.common.ExternalProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
public class FrameOcrService {
    private static final Logger log = LoggerFactory.getLogger(FrameOcrService.class);
    private final boolean enabled;
    private final String executable;
    private final String languages;

    public FrameOcrService(@Value("${game-narrator.ocr.enabled:true}") boolean enabled,
                           @Value("${game-narrator.ocr.executable:tesseract}") String executable,
                           @Value("${game-narrator.ocr.languages:chi_sim+eng}") String languages) {
        this.enabled = enabled;
        this.executable = executable;
        this.languages = languages;
    }

    public FrameUnderstanding enrich(FrameUnderstanding frame) {
        // The vision model is the default OCR engine. Tesseract only fills frames for which
        // the model did not return visible text, so a missing optional executable is harmless.
        if (!enabled || (frame.ocrText() != null && !frame.ocrText().isBlank())) return frame;
        try {
            var result = ExternalProcessRunner.run(List.of(executable, Path.of(frame.imagePath()).toString(),
                    "stdout", "-l", languages, "--psm", "6"), Duration.ofSeconds(20));
            String text = result.output().replaceAll("\\s+", " ").strip();
            if (result.exitCode() != 0 || text.isBlank()) return frame;
            if (text.length() > 500) text = text.substring(0, 500);
            return new FrameUnderstanding(frame.index(), frame.timestampSeconds(), frame.imagePath(),
                    frame.description() + "；界面文字：" + text, frame.eventType(),
                    Math.min(100, frame.excitementScore() + keywordBonus(text)), text, frame.rawJson());
        } catch (Exception exception) {
            log.debug("FRAME_OCR_SKIPPED frame={} reason={}", frame.index(), exception.getMessage());
            return frame;
        }
    }

    private int keywordBonus(String text) {
        String value = text.toLowerCase();
        return List.of("胜利", "失败", "击杀", "得分", "boss", "victory", "defeat", "kill")
                .stream().anyMatch(value::contains) ? 8 : 0;
    }
}
