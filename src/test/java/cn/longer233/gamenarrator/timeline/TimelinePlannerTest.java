package cn.longer233.gamenarrator.timeline;

import cn.longer233.gamenarrator.highlight.HighlightClip;
import cn.longer233.gamenarrator.script.ScriptSegment;
import cn.longer233.gamenarrator.voice.VoiceSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TimelinePlannerTest {
    @TempDir Path tempDir;

    @Test
    void alignsSourceClipsScriptsAndVoiceOnOutputTimeline() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path highlights = tempDir.resolve("highlights.json");
        Path scripts = tempDir.resolve("script.json");
        Path voices = tempDir.resolve("voices.json");
        Path wav = tempDir.resolve("voice.wav");
        writeSilentWav(wav, 2);
        mapper.writeValue(highlights.toFile(), Map.of("clips", List.of(
                new HighlightClip(1, 10, 22, 14, "战斗", "画面", 80, 90))));
        mapper.writeValue(scripts.toFile(), Map.of("segments", List.of(
                new ScriptSegment(1, 10, 22, "台词", "字幕", "震动"))));
        mapper.writeValue(voices.toFile(), Map.of("segments", List.of(
                new VoiceSegment(1, wav.toString(), "台词"))));

        TimelinePlanningResult result = new TimelinePlanner(mapper, new TimelineValidator())
                .plan(highlights, scripts, voices);

        assertThat(result.outputDurationSeconds()).isEqualTo(12);
        assertThat(result.overflowCount()).isZero();
        assertThat(result.segments().getFirst().voiceDurationSeconds()).isBetween(1.99, 2.01);
        assertThat(Path.of(result.timelinePath())).exists();
    }

    private void writeSilentWav(Path output, int seconds) throws Exception {
        AudioFormat format = new AudioFormat(16_000, 16, 1, true, false);
        byte[] bytes = new byte[16_000 * 2 * seconds];
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(bytes), format, 16_000L * seconds)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output.toFile());
        }
    }
}
