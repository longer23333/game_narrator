package cn.longer233.gamenarrator.mobile;

public final class ProjectSnapshotCommand implements EditCommand {
    private final ProjectState state;
    private final ProjectSnapshot before;
    private final String description;
    private ProjectSnapshot after;

    public ProjectSnapshotCommand(ProjectState state, String description) {
        this.state = state;
        this.before = state.capture();
        this.description = description == null || description.isBlank() ? "编辑" : description;
    }

    public void captureAfter() {
        this.after = state.capture();
    }

    public String description() { return description; }

    @Override public void undo() {
        state.restore(before);
    }

    @Override public void redo() {
        state.restore(after == null ? before : after);
    }
}
