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

    @Test
    void refreshesOnlyRequestedSegmentAndPreservesTimelineBoundaries() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path highlights = tempDir.resolve("local-highlights.json");
        Path scripts = tempDir.resolve("local-script.json");
        Path voices = tempDir.resolve("local-voices.json");
        Path firstWav = tempDir.resolve("first.wav");
        Path revisedWav = tempDir.resolve("revised.wav");
        Path thirdWav = tempDir.resolve("third.wav");
        writeSilentWav(firstWav, 1);
        writeSilentWav(revisedWav, 3);
        writeSilentWav(thirdWav, 1);
        List<HighlightClip> clips = List.of(
                new HighlightClip(1, 10, 14, 12, "A", "one", 70, 70),
                new HighlightClip(2, 20, 24, 22, "B", "two", 80, 80),
                new HighlightClip(3, 30, 34, 32, "C", "three", 90, 90));
        mapper.writeValue(highlights.toFile(), Map.of("clips", clips));
        mapper.writeValue(scripts.toFile(), Map.of("segments", List.of(
                new ScriptSegment(1, 10, 14, "first", "s1", "e1"),
                new ScriptSegment(2, 20, 24, "revised", "new subtitle", "new effect"),
                new ScriptSegment(3, 30, 34, "third", "s3", "e3"))));
        mapper.writeValue(voices.toFile(), Map.of("segments", List.of(
                new VoiceSegment(1, firstWav.toString(), "first"),
                new VoiceSegment(2, revisedWav.toString(), "revised"),
                new VoiceSegment(3, thirdWav.toString(), "third"))));
        Path timeline = tempDir.resolve("timeline.json");
        List<TimelineSegment> original = List.of(
                new TimelineSegment(1, 0, 4, 10, 14, "first", "s1", "e1", firstWav.toString(), 1, false),
                new TimelineSegment(2, 4, 8, 20, 24, "old", "old subtitle", "old effect", "old.wav", 1, false),
                new TimelineSegment(3, 8, 12, 30, 34, "third", "s3", "e3", thirdWav.toString(), 1, false));
        mapper.writeValue(timeline.toFile(), Map.of("version", 1, "outputDurationSeconds", 12,
                "voiceOverflowCount", 0, "segments", original));

        TimelinePlanningResult result = new TimelinePlanner(mapper, new TimelineValidator())
                .refreshSegment(timeline, highlights, scripts, voices, 2);

        assertThat(result.segments().get(0)).isEqualTo(original.get(0));
        assertThat(result.segments().get(2)).isEqualTo(original.get(2));
        TimelineSegment revised = result.segments().get(1);
        assertThat(revised.narration()).isEqualTo("revised");
        assertThat(revised.subtitle()).isEqualTo("new subtitle");
        assertThat(revised.effectCue()).isEqualTo("new effect");
        assertThat(revised.voicePath()).isEqualTo(revisedWav.toString());
        assertThat(revised.voiceDurationSeconds()).isBetween(2.99, 3.01);
        assertThat(revised.outputStartSeconds()).isEqualTo(4);
        assertThat(revised.outputEndSeconds()).isEqualTo(8);
        assertThat(revised.sourceStartSeconds()).isEqualTo(20);
        assertThat(revised.sourceEndSeconds()).isEqualTo(24);
        assertThat(mapper.readTree(timeline.toFile()).path("localizedRevision").path("clipIndex").asInt())
                .isEqualTo(2);
    }

    @Test
    void transitionOverlapUpdatesOutputCoordinatesForAudioAndSubtitleSynchronization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path highlights = tempDir.resolve("transition-highlights.json");
        Path scripts = tempDir.resolve("transition-script.json");
        Path voices = tempDir.resolve("transition-voices.json");
        Path wav = tempDir.resolve("transition.wav");
        writeSilentWav(wav, 1);
        mapper.writeValue(highlights.toFile(), Map.of("clips", List.of(
                new HighlightClip(1, 0, 4, 2, "A", "one", 70, 70),
                new HighlightClip(2, 5, 9, 7, "B", "two", 80, 80))));
        mapper.writeValue(scripts.toFile(), Map.of("segments", List.of(
                new ScriptSegment(1, 0, 4, "first", "s1", "hard cut"),
                new ScriptSegment(2, 5, 9, "second", "s2", "叠化"))));
        mapper.writeValue(voices.toFile(), Map.of("segments", List.of(
                new VoiceSegment(1, wav.toString(), "first"), new VoiceSegment(2, wav.toString(), "second"))));

        var result = new TimelinePlanner(mapper, new TimelineValidator()).plan(highlights, scripts, voices);

        assertThat(result.segments().get(1).transitionType()).isEqualTo("DISSOLVE");
        assertThat(result.segments().get(1).transitionDurationSeconds()).isEqualTo(.45);
        assertThat(result.segments().get(1).outputStartSeconds()).isEqualTo(3.55);
        assertThat(result.outputDurationSeconds()).isEqualTo(7.55);
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
