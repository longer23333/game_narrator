package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.audio.SoundCue;
import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderAudioMixBuilderTest {
    private final RenderAudioMixBuilder builder = new RenderAudioMixBuilder();

    @Test
    void buildsVoiceCueAndBackgroundMixWithStableInputIndexes() {
        List<TimelineSegment> segments = segments();
        SoundCue cue = new SoundCue(1, 1.25, "IMPACT", "impact.wav", 0.6);
        var background = new RenderAssetResolver.RenderAsset(1, "BGM", "BACKGROUND_AUDIO", "CENTER",
                false, Path.of("music.wav"), "music");

        RenderAudioMixBuilder.AudioMixPlan plan = builder.build(
                segments, List.of(cue), List.of(background), 0.2);

        assertThat(plan.filterGraph()).contains(
                "[0:a]volume=0.200[bg]",
                "[1:a]atempo=1.263,adelay=0|0[v0]",
                "[2:a]adelay=5000|5000[v1]",
                "[3:a]volume=0.600,adelay=1250|1250[s0]",
                "[4:a]atrim=0:10.000,volume='if(between(t,0.000,5.000),0.140,if(between(t,5.000,10.000),0.140,0.140))':eval=frame[x0]",
                "amix=inputs=4:duration=longest:normalize=0",
                "sidechaincompress=threshold=0.02:ratio=8",
                "[aout]");
        assertThat(plan.subtitleInput()).isEqualTo(5);
    }

    @Test
    void alignsExternalSoundEffectToItsStoryboardSegment() {
        var soundEffect = new RenderAssetResolver.RenderAsset(2, "SFX", "SOUND_EFFECT", "CENTER",
                false, Path.of("effect.wav"), "effect");

        RenderAudioMixBuilder.AudioMixPlan plan = builder.build(segments(), List.of(), List.of(soundEffect), 0.15);

        assertThat(plan.filterGraph()).contains(
                "[3:a]atrim=0:5.000,asetpts=PTS-STARTPTS,volume=0.480,adelay=5000|5000[x0]");
        assertThat(plan.subtitleInput()).isEqualTo(4);
    }

    @Test
    void appliesTimelineWindowVolumeAndFadesToLicensedSoundEffect() {
        var soundEffect = new RenderAssetResolver.RenderAsset(2, "SFX", "SOUND_EFFECT", "AUDIO_TRACK",
                false, Path.of("licensed.wav"), "licensed", .75, 3.25, 38, "NONE", 2,
                72, .2, .35, "CC0", "Creator / Source");

        String graph = builder.build(segments(), List.of(), List.of(soundEffect), .15).filterGraph();

        assertThat(graph).contains("atrim=0:2.500,asetpts=PTS-STARTPTS,volume=0.720",
                "afade=t=in:st=0:d=0.200", "afade=t=out:st=2.150:d=0.350",
                "adelay=5750|5750[x0]");
    }

    @Test
    void mapsNarrativeMusicIntensityToBackgroundVolumeCurve() {
        var background = new RenderAssetResolver.RenderAsset(1, "BGM", "BACKGROUND_AUDIO", "CENTER",
                false, Path.of("music.wav"), "music");
        List<TimelineSegment> segments = List.of(
                new TimelineSegment(1, 0, 4, 0, 4, "n1", "s1", "NARRATIVE_SETUP pace=.90 music=0.25", "v1", 3, false),
                new TimelineSegment(2, 4, 8, 4, 8, "n2", "s2", "NARRATIVE_CLIMAX pace=1.28 music=1.00", "v2", 3, false));

        String graph = builder.build(segments, List.of(), List.of(background), .2).filterGraph();

        assertThat(graph).contains("between(t,0.000,4.000),0.100", "between(t,4.000,8.000),0.220");
    }

    @Test
    void rejectsEmptyTimeline() {
        assertThatThrownBy(() -> builder.build(List.of(), List.of(), List.of(), 0.2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("剪辑时间线不能为空");
    }

    private List<TimelineSegment> segments() {
        return List.of(
                new TimelineSegment(1, 0, 5, 0, 5, "旁白一", "字幕一", "", "voice-1.wav", 6, false),
                new TimelineSegment(2, 5, 10, 5, 10, "旁白二", "字幕二", "", "voice-2.wav", 4, false));
    }
}
