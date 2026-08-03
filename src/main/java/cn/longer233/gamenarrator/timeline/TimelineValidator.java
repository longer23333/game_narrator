package cn.longer233.gamenarrator.timeline;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TimelineValidator {
    private static final double EPSILON = 0.001;

    public void validate(List<TimelineSegment> segments, double outputDurationSeconds) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalStateException("时间线没有可渲染片段");
        }
        if (!Double.isFinite(outputDurationSeconds) || outputDurationSeconds <= 0) {
            throw new IllegalStateException("时间线输出时长无效");
        }
        Set<Integer> sequences = new HashSet<>();
        double expectedStart = 0;
        for (TimelineSegment segment : segments) {
            String prefix = "时间线片段 " + segment.sequence() + "：";
            if (segment.sequence() <= 0 || !sequences.add(segment.sequence())) {
                throw new IllegalStateException(prefix + "序号无效或重复");
            }
            requireFiniteRange(prefix, segment.outputStartSeconds(), segment.outputEndSeconds(), "输出");
            requireFiniteRange(prefix, segment.sourceStartSeconds(), segment.sourceEndSeconds(), "源视频");
            if (Math.abs(segment.outputStartSeconds() - expectedStart) > EPSILON) {
                throw new IllegalStateException(prefix + "与上一片段之间存在空隙或重叠");
            }
            if (!Double.isFinite(segment.voiceDurationSeconds()) || segment.voiceDurationSeconds() <= 0) {
                throw new IllegalStateException(prefix + "配音时长无效");
            }
            if (segment.voicePath() == null || segment.voicePath().isBlank()
                    || !Files.isRegularFile(Path.of(segment.voicePath()))) {
                throw new IllegalStateException(prefix + "配音文件不存在");
            }
            // Narration and subtitle are independent optional tracks.
            expectedStart = segment.outputEndSeconds();
        }
        if (Math.abs(expectedStart - outputDurationSeconds) > EPSILON) {
            throw new IllegalStateException("时间线总时长与最后片段结束时间不一致");
        }
    }

    private void requireFiniteRange(String prefix, double start, double end, String label) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start) {
            throw new IllegalStateException(prefix + label + "时间范围无效");
        }
    }
}
