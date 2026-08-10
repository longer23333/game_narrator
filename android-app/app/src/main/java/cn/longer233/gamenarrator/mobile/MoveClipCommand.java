package cn.longer233.gamenarrator.mobile;

import java.util.List;

public final class MoveClipCommand implements EditCommand {
    private final List<TimelineClip> target;
    private final ProjectState state;
    private final int index;
    private final int delta;
    private final String description;
    private final ProjectSnapshot before;
    private ProjectSnapshot after;

    public MoveClipCommand(List<TimelineClip> target, ProjectState state, int index, int delta, String description) {
        this.target = target;
        this.state = state;
        this.index = index;
        this.delta = delta;
        this.description = description == null || description.isBlank() ? "移动片段顺序" : description;
        this.before = state.capture();
    }

    public String description() { return description; }

    @Override public void undo() {
        state.restore(before);
    }

    @Override public void redo() {
        if (after != null) {
            state.restore(after);
            return;
        }
        int targetIndex = index + delta;
        TimelineClip moved = target.remove(index);
        target.add(Math.max(0, Math.min(targetIndex, target.size())), moved);
        after = state.capture();
    }
}
