package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;

public final class SnapshotEditCommand implements EditCommand {
    private final List<TimelineClip> target;
    private final List<TimelineClip> before;
    private final String description;
    private List<TimelineClip> after;

    public SnapshotEditCommand(List<TimelineClip> target) {
        this(target, "编辑");
    }

    public SnapshotEditCommand(List<TimelineClip> target, String description) {
        this.target = target;
        this.before = copy(target);
        this.description = description == null || description.isBlank() ? "编辑" : description;
    }

    public void captureAfter() {
        this.after = copy(target);
    }

    public String description() { return description; }

    @Override public void undo() {
        apply(before);
    }

    @Override public void redo() {
        apply(after == null ? before : after);
    }

    private void apply(List<TimelineClip> snapshot) {
        target.clear();
        target.addAll(copy(snapshot));
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
