package cn.longer233.gamenarrator.timeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimelineValidatorTest {
    @TempDir Path tempDirectory;
    private final TimelineValidator validator = new TimelineValidator();

    @Test
    void acceptsContinuousTimelineWithExistingVoice() throws Exception {
        Path voice = Files.write(tempDirectory.resolve("voice.wav"), new byte[]{1});
        assertDoesNotThrow(() -> validator.validate(List.of(segment(1, 0, 2, voice)), 2));
    }

    @Test
    void rejectsGapBetweenSegments() throws Exception {
        Path voice = Files.write(tempDirectory.resolve("voice.wav"), new byte[]{1});
        assertThrows(IllegalStateException.class, () -> validator.validate(List.of(
                segment(1, 0, 2, voice), segment(2, 2.5, 3.5, voice)), 3.5));
    }

    @Test
    void rejectsMissingVoiceArtifact() {
        assertThrows(IllegalStateException.class, () -> validator.validate(List.of(
                segment(1, 0, 2, tempDirectory.resolve("missing.wav"))), 2));
    }

    private TimelineSegment segment(int sequence, double start, double end, Path voice) {
        return new TimelineSegment(sequence, start, end, start, end,
                "解说", "字幕", "转场", voice.toString(), 1, false);
    }
}
