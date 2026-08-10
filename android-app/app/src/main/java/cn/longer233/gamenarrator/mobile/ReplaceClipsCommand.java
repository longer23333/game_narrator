package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;

public final class ReplaceClipsCommand implements EditCommand {
    private final ProjectState state;
    private final List<TimelineClip> replacement;
    private final String description;
    private final ProjectSnapshot before;
    private ProjectSnapshot after;

    public ReplaceClipsCommand(ProjectState state, List<TimelineClip> replacement, String description) {
        this.state = state;
        this.replacement = copy(replacement);
        this.description = description == null || description.isBlank() ? "替换时间线" : description;
        this.before = state.capture();
    }

    public String description() { return description; }

    @Override public void undo() {
        state.restore(before);
    }

    @Override public void redo() {
        if (after == null) {
            ProjectSnapshot current = state.capture();
            ProjectSnapshot replacementSnapshot = new ProjectSnapshot(replacement, current.subtitles(),
                    current.placements(), current.tracks(), current.keyframes(), current.audioConfigs(),
                    current.visualConfigs(), current.reviews(), current.voices());
            state.restore(replacementSnapshot);
            after = state.capture();
        } else {
            state.restore(after);
        }
    }

    private static List<TimelineClip> copy(List<TimelineClip> source) {
        List<TimelineClip> result = new ArrayList<>();
        if (source != null) {
            for (TimelineClip clip : source) {
                if (clip != null) result.add(new TimelineClip(clip));
            }
        }
        return result;
    }
}
