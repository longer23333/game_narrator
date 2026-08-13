package cn.longer233.gamenarrator.render;

import cn.longer233.gamenarrator.timeline.TimelineSegment;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
public class TimelineTransitionGraphBuilder {
    public TransitionGraph build(List<TimelineSegment> segments) {
        if (segments.size() < 2 || segments.stream().skip(1).allMatch(this::hardCut))
            return new TransitionGraph("", 0, false);
        StringBuilder graph = new StringBuilder();
        for (int index = 0; index < segments.size(); index++) {
            graph.append('[').append(index).append(":v]settb=AVTB,setpts=PTS-STARTPTS[v")
                    .append(index).append("in];[").append(index).append(":a]asetpts=PTS-STARTPTS[a")
                    .append(index).append("in];");
        }
        double duration = segmentDuration(segments.getFirst());
        String video = "v0in";
        String audio = "a0in";
        for (int index = 1; index < segments.size(); index++) {
            TimelineSegment segment = segments.get(index);
            double overlap = segment.transitionDurationSeconds();
            double offset = duration - overlap;
            String nextVideo = "vx" + index;
            String nextAudio = "ax" + index;
            if (hardCut(segment)) {
                graph.append('[').append(video).append("][").append(audio).append("][")
                        .append('v').append(index).append("in][a").append(index).append("in]concat=n=2:v=1:a=1[")
                        .append(nextVideo).append("][").append(nextAudio).append("];");
                duration += segmentDuration(segment);
            } else {
                graph.append('[').append(video).append("][v").append(index).append("in]xfade=transition=")
                        .append(ffmpegTransition(segment)).append(":duration=").append(decimal(overlap))
                        .append(":offset=").append(decimal(offset)).append('[').append(nextVideo).append("];");
                graph.append('[').append(audio).append("][a").append(index).append("in]acrossfade=d=")
                        .append(decimal(overlap)).append(":c1=").append(audioCurve(segment.transitionCurve()))
                        .append(":c2=").append(audioCurve(segment.transitionCurve())).append('[').append(nextAudio).append("];");
                duration += segmentDuration(segment) - overlap;
            }
            video = nextVideo;
            audio = nextAudio;
        }
        graph.append('[').append(video).append("]null[vout];[").append(audio).append("]anull[aout]");
        return new TransitionGraph(graph.toString(), duration, true);
    }

    public TransitionGraph hardCutFallback(List<TimelineSegment> segments) {
        if (segments.size() < 2) return new TransitionGraph("", 0, false);
        StringBuilder graph = new StringBuilder();
        double duration = 0;
        for (int index = 0; index < segments.size(); index++) {
            double trim = index == 0 ? 0 : Math.max(0, segments.get(index).transitionDurationSeconds());
            graph.append('[').append(index).append(":v]trim=start=").append(decimal(trim))
                    .append(",settb=AVTB,setpts=PTS-STARTPTS[fv").append(index).append("];[")
                    .append(index).append(":a]atrim=start=").append(decimal(trim))
                    .append(",asetpts=PTS-STARTPTS[fa").append(index).append("];");
            duration += segmentDuration(segments.get(index)) - trim;
        }
        for (int index = 0; index < segments.size(); index++) {
            graph.append("[fv").append(index).append("][fa").append(index).append(']');
        }
        graph.append("concat=n=").append(segments.size()).append(":v=1:a=1[vout][aout]");
        return new TransitionGraph(graph.toString(), duration, true);
    }

    private boolean hardCut(TimelineSegment segment) {
        return "HARD_CUT".equals(segment.transitionType()) || segment.transitionDurationSeconds() <= 0;
    }
    private double segmentDuration(TimelineSegment segment) {
        return segment.sourceEndSeconds() - segment.sourceStartSeconds();
    }
    private String ffmpegTransition(TimelineSegment segment) {
        return switch (String.valueOf(segment.transitionType())) {
            case "PUSH" -> "RIGHT".equals(segment.transitionDirection()) ? "slideright" : "slideleft";
            case "ANIME_IMPACT" -> "radial";
            case "FADE", "DISSOLVE" -> "fade";
            default -> "fade";
        };
    }
    private String audioCurve(String curve) { return "EXP".equals(curve) ? "exp" : "tri"; }
    private String decimal(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    public record TransitionGraph(String filterGraph, double outputDurationSeconds, boolean enabled) { }
}
