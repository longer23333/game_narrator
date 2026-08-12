package cn.longer233.gamenarrator.vision;

import cn.longer233.gamenarrator.media.SceneFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventWindowSecondPassTest {
    @Test
    void selectsPreAndPostFramesForHighValueCenterOnly() {
        SceneFrame pre = frame(1, 9.65, "pre", 10.0);
        SceneFrame center = frame(2, 10.0, "center", 10.0);
        SceneFrame post = frame(3, 10.35, "post", 10.0);
        SceneFrame quietPre = frame(4, 19.65, "quiet-pre", 20.0);
        SceneFrame quietCenter = frame(5, 20.0, "quiet-center", 20.0);
        SceneFrame quietPost = frame(6, 20.35, "quiet-post", 20.0);

        List<SceneFrame> selected = EventWindowSecondPass.select(
                List.of(pre, center, post, quietPre, quietCenter, quietPost),
                List.of(center, quietCenter),
                List.of(analysis(center, "战斗", 88, "击杀"), analysis(quietCenter, "其他", 20, "")), 10);

        assertThat(selected).extracting(SceneFrame::imagePath).containsExactly("pre", "post");
    }

    @Test
    void respectsSecondPassCeiling() {
        SceneFrame pre = frame(1, .65, "pre", 1.0);
        SceneFrame center = frame(2, 1.0, "center", 1.0);
        SceneFrame post = frame(3, 1.35, "post", 1.0);
        assertThat(EventWindowSecondPass.select(List.of(pre, center, post), List.of(center),
                List.of(analysis(center, "胜利", 70, "")), 1)).hasSize(1);
    }

    private SceneFrame frame(int index, double time, String path, double anchor) {
        return new SceneFrame(index, time, path, "SCENE_CHANGE_WINDOW", anchor);
    }

    private FrameUnderstanding analysis(SceneFrame frame, String event, int score, String ocr) {
        return new FrameUnderstanding(frame.index(), frame.timestampSeconds(), frame.imagePath(),
                "description", event, score, ocr, "{}");
    }
}
