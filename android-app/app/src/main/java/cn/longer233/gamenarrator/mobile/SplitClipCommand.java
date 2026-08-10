package cn.longer233.gamenarrator.mobile;

import java.util.List;

public final class SplitClipCommand implements EditCommand {
    private final List<TimelineClip> target;
    private final ProjectState state;
    private final int index;
    private final long atMs;
    private final String description;
    private final ProjectSnapshot before;
    private ProjectSnapshot after;

    public SplitClipCommand(List<TimelineClip> target, ProjectState state, int index, long atMs, String description) {
        this.target = target;
        this.state = state;
        this.index = index;
        this.atMs = atMs;
        this.description = description == null || description.isBlank() ? "刀片分割片段" : description;
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
        TimelineClip clip = target.get(index);
        TimelineClip right = new TimelineClip(clip.uri(), clip.name(), atMs, clip.endMs());
        right.update(atMs, clip.endMs(), clip.muted(), clip.subtitle());
        right.updateCreativeText(clip.subtitle(), clip.narration(), clip.effectCue());
        clip.update(clip.startMs(), atMs, clip.muted(), clip.subtitle());
        target.add(index + 1, right);
        after = state.capture();
    }
}
