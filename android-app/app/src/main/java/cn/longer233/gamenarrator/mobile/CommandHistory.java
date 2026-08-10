package cn.longer233.gamenarrator.mobile;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CommandHistory {
    public static final int DEFAULT_LIMIT = 50;

    private final int limit;
    private final Deque<EditCommand> undo = new ArrayDeque<>();
    private final Deque<EditCommand> redo = new ArrayDeque<>();

    public CommandHistory() {
        this(DEFAULT_LIMIT);
    }

    public CommandHistory(int limit) {
        this.limit = Math.max(1, limit);
    }

    public void push(EditCommand command) {
        if (command == null) return;
        undo.push(command);
        while (undo.size() > limit) undo.removeLast();
        redo.clear();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public int size() { return undo.size(); }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        EditCommand command = undo.pop();
        redo.push(command);
        command.undo();
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        EditCommand command = redo.pop();
        undo.push(command);
        command.redo();
        return true;
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
