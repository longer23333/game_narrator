package cn.longer233.gamenarrator.audio;

import cn.longer233.gamenarrator.effect.EffectPlan;
import cn.longer233.gamenarrator.effect.TransitionType;
import cn.longer233.gamenarrator.effect.VisualEffectType;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProceduralSoundEffectLibraryTest {
    @Test
    void createsDeterministicImpactSound(@TempDir Path directory) throws Exception {
        var segment = new TimelineSegment(1, 2, 6, 10, 14,
                "反转", "反转", "冲击", "voice.wav", 1, false);
        var plan = new EffectPlan(List.of(VisualEffectType.WHITE_FLASH),
                TransitionType.ANIME_IMPACT, "test");

        List<SoundCue> cues = new ProceduralSoundEffectLibrary()
                .create(directory, List.of(segment), List.of(plan));

        assertThat(cues).hasSize(1);
        assertThat(cues.getFirst().type()).isEqualTo("IMPACT");
        assertThat(Files.size(Path.of(cues.getFirst().audioPath()))).isGreaterThan(10_000);
    }
}
