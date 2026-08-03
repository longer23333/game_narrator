package cn.longer233.gamenarrator.timeline;

import java.util.List;

public record TimelinePlanningResult(
        String timelinePath,
        double outputDurationSeconds,
        int overflowCount,
        List<TimelineSegment> segments
) {
}
