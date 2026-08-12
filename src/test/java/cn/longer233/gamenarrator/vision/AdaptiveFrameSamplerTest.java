package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.media.SceneFrame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveFrameSamplerTest {
    @Test
    void increasesCoverageForLongVideosAndKeepsBothEnds() {
        List<SceneFrame> scenes = IntStream.range(0, 1_000)
                .mapToObj(index -> new SceneFrame(index, index * 10.8, index + ".jpg"))
                .toList();

        List<SceneFrame> selected = AdaptiveFrameSampler.sample(scenes, 0);

        assertThat(selected).hasSize(720);
        assertThat(selected.getFirst()).isEqualTo(scenes.getFirst());
        assertThat(selected.getLast()).isEqualTo(scenes.getLast());
        assertThat(selected).hasSizeGreaterThan(240);
    }

    @Test
    void samplesShortVideosMoreDenselyAndHonorsOperatorCeiling() {
        assertThat(AdaptiveFrameSampler.desiredFrameCount(120)).isEqualTo(25);
        assertThat(AdaptiveFrameSampler.desiredFrameCount(1_800)).isEqualTo(181);
        assertThat(AdaptiveFrameSampler.desiredFrameCount(7_200)).isEqualTo(481);

        List<SceneFrame> scenes = IntStream.range(0, 100)
                .mapToObj(index -> new SceneFrame(index, index * 2.0, index + ".jpg"))
                .toList();
        assertThat(AdaptiveFrameSampler.sample(scenes, 16)).hasSize(16);
    }

    @Test
    void reservesFirstPassCapacityForEventCentersWithoutIncludingPreAndPostFrames() {
        List<SceneFrame> frames = new java.util.ArrayList<>(IntStream.range(0, 30)
                .mapToObj(index -> new SceneFrame(index, index * 5.0, "scene-" + index + ".jpg"))
                .toList());
        frames.add(new SceneFrame(100, 10.65, "pre.jpg", "AUDIO_PEAK_WINDOW", 11.0));
        frames.add(new SceneFrame(101, 11.0, "center.jpg", "AUDIO_PEAK_WINDOW", 11.0));
        frames.add(new SceneFrame(102, 11.35, "post.jpg", "AUDIO_PEAK_WINDOW", 11.0));

        List<SceneFrame> selected = AdaptiveFrameSampler.sample(frames, 8);

        assertThat(selected).hasSize(8).extracting(SceneFrame::imagePath).contains("center.jpg");
        assertThat(selected).noneMatch(frame -> frame.imagePath().equals("pre.jpg") || frame.imagePath().equals("post.jpg"));
    }
}
