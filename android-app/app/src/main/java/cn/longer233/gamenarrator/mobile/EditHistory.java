package cn.longer233.gamenarrator.mobile;

import java.util.ArrayDeque;
import java.util.Deque;

public final class EditHistory {
    private final Deque<EditSnapshot> undo = new ArrayDeque<>();
    private final Deque<EditSnapshot> redo = new ArrayDeque<>();
    public void push(EditSnapshot value) { undo.push(value); redo.clear(); trim(); }
    public EditSnapshot undo(EditSnapshot current) { if (undo.isEmpty()) return current; redo.push(current); return undo.pop(); }
    public EditSnapshot redo(EditSnapshot current) { if (redo.isEmpty()) return current; undo.push(current); return redo.pop(); }
    private void trim() { while (undo.size() > 50) undo.removeLast(); }
}
