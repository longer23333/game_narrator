package cn.longer233.gamenarrator.timeline;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TimelineTransitionPlannerTest {
    private final TimelineTransitionPlanner planner = new TimelineTransitionPlanner();

    @Test void parameterizesDirectionCurveAndDurationFromCue() {
        var boundary = planner.boundary("向右推镜冲刺", 2, 4, 3);
        assertThat(boundary.type()).isEqualTo("PUSH");
        assertThat(boundary.direction()).isEqualTo("RIGHT");
        assertThat(boundary.durationSeconds()).isEqualTo(.32);
        assertThat(boundary.curve()).isEqualTo("TRI");
    }

    @Test void constrainsShortAdjacentClipsAndFallsBackToHardCut() {
        var boundary = planner.boundary("叠化", 2, .1, .12);
        assertThat(boundary.type()).isEqualTo("HARD_CUT");
        assertThat(boundary.durationSeconds()).isZero();
        assertThat(boundary.reason()).isEqualTo("adjacent-clips-too-short");
    }
}
