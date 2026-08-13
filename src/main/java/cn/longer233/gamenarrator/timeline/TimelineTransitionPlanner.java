package cn.longer233.gamenarrator.timeline;

import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class TimelineTransitionPlanner {
    public TransitionBoundary boundary(String cue, int sequence, double previousDuration, double currentDuration) {
        if (sequence <= 1) return TransitionBoundary.hardCut("first-clip");
        String value = cue == null ? "" : cue.toLowerCase(Locale.ROOT);
        String type = contains(value, "动漫", "冲击", "impact") ? "ANIME_IMPACT"
                : contains(value, "推", "冲刺", "push") ? "PUSH"
                : contains(value, "叠化", "溶解", "回忆", "dissolve") ? "DISSOLVE"
                : contains(value, "淡入", "淡出", "fade") ? "FADE" : "HARD_CUT";
        if ("HARD_CUT".equals(type)) return TransitionBoundary.hardCut("cue-hard-cut");
        double requested = "ANIME_IMPACT".equals(type) ? .18 : "PUSH".equals(type) ? .32 : .45;
        double duration = Math.min(requested, Math.min(previousDuration, currentDuration) * .35);
        if (duration < .08) return TransitionBoundary.hardCut("adjacent-clips-too-short");
        String direction = value.contains("向右") || value.contains("right") ? "RIGHT" : "LEFT";
        return new TransitionBoundary(type, duration, direction,
                "ANIME_IMPACT".equals(type) ? "EXP" : "TRI", "cue");
    }

    private boolean contains(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    public record TransitionBoundary(String type, double durationSeconds, String direction, String curve,
                                     String reason) {
        static TransitionBoundary hardCut(String reason) {
            return new TransitionBoundary("HARD_CUT", 0, "LEFT", "TRI", reason);
        }
    }
}
