package cn.longer233.gamenarrator.mobile;

import java.util.List;

public final class DeleteClipCommand implements EditCommand {
    private final List<TimelineClip> target;
    private final ProjectState state;
    private final int index;
    private final String description;
    private final ProjectSnapshot before;
    private ProjectSnapshot after;

    public DeleteClipCommand(List<TimelineClip> target, ProjectState state, int index, String description) {
        this.target = target;
        this.state = state;
        this.index = index;
        this.description = description == null || description.isBlank() ? "删除片段" : description;
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
        target.remove(index);
        after = state.capture();
    }
}
