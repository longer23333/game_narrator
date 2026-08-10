package cn.longer233.gamenarrator.mobile;

import java.util.List;

/**
 * Durable project edit history: serialized {@link ProjectSnapshot} payloads in
 * RAM mirror plus SQLite, capped by {@link HybridEditHistory}.
 */
public final class PersistentSnapshotHistory {
    private final HybridEditHistory history;

    public PersistentSnapshotHistory(EditHistoryStore store) {
        this.history = new HybridEditHistory(store);
    }

    public void push(long projectId, ProjectSnapshot snapshot) throws Exception {
        history.push(projectId, ProjectSnapshotJson.toJson(snapshot));
    }

    public ProjectSnapshot latest(long projectId) throws Exception {
        List<String> recent = history.recent(projectId, 1);
        return recent.isEmpty() ? null : ProjectSnapshotJson.fromJson(recent.get(0));
    }

    public void clear(long projectId) {
        history.clear(projectId);
    }

    public int ramSize() {
        return history.ramSize();
    }
}
