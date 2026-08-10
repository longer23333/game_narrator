package cn.longer233.gamenarrator.mobile;

import java.util.List;

public final class TimelineController {
    public interface Listener {
        void onTimelineChanged();
        void onStatus(String message);
    }

    private final List<TimelineClip> clips;
    private final ProjectState projectState;
    private final CommandHistory history = new CommandHistory();
    private final Listener listener;
    private ProjectSnapshotCommand pendingCommand;

    public TimelineController(List<TimelineClip> clips, ProjectState projectState, Listener listener) {
        this.clips = clips;
        this.projectState = projectState;
        this.listener = listener;
    }

    public boolean canUndo() { return history.canUndo(); }
    public boolean canRedo() { return history.canRedo(); }
    public int historySize() { return history.size(); }

    public void clearHistory() {
        history.clear();
        pendingCommand = null;
    }

    public void pushSnapshot() {
        pushSnapshot("编辑");
    }

    public void pushSnapshot(String description) {
        pendingCommand = new ProjectSnapshotCommand(projectState, description);
        history.push(pendingCommand);
    }

    public void captureSnapshot() {
        if (pendingCommand != null) {
            pendingCommand.captureAfter();
            pendingCommand = null;
        }
    }

    public boolean undo() {
        if (!history.undo()) {
            listener.onStatus("没有可以撤回的操作。");
            return false;
        }
        pendingCommand = null;
        return true;
    }

    public boolean redo() {
        if (!history.redo()) {
            listener.onStatus("没有可以恢复的操作。");
            return false;
        }
        pendingCommand = null;
        return true;
    }

    public boolean split(int index, long at) {
        if (index < 0 || index >= clips.size()) {
            listener.onStatus("请先选择片段。");
            return false;
        }
        TimelineClip clip = clips.get(index);
        if (at <= clip.startMs() || at >= clip.endMs()) {
            listener.onStatus("请把播放头移到片段内部再分割。");
            return false;
        }
        SplitClipCommand command = new SplitClipCommand(clips, projectState, index, at, "刀片分割片段");
        history.push(command);
        command.redo();
        listener.onTimelineChanged();
        return true;
    }

    public boolean move(int index, int delta) {
        if (index < 0 || index >= clips.size()) return false;
        int target = index + delta;
        if (target < 0 || target >= clips.size()) return false;
        MoveClipCommand command = new MoveClipCommand(clips, projectState, index, delta, "移动片段顺序");
        history.push(command);
        command.redo();
        listener.onTimelineChanged();
        return true;
    }

    public boolean remove(int index) {
        if (index < 0 || index >= clips.size()) return false;
        DeleteClipCommand command = new DeleteClipCommand(clips, projectState, index, "删除片段");
        history.push(command);
        command.redo();
        listener.onTimelineChanged();
        return true;
    }

    public boolean replace(List<TimelineClip> replacement, String description) {
        if (replacement == null) return false;
        ReplaceClipsCommand command = new ReplaceClipsCommand(projectState, replacement, description);
        history.push(command);
        command.redo();
        listener.onTimelineChanged();
        return true;
    }
}
