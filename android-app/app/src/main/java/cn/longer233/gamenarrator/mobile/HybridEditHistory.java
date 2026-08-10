package cn.longer233.gamenarrator.mobile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Hybrid edit history: the newest 10 payloads stay in RAM as a fast mirror,
 * while up to 50 are persisted through {@link EditHistoryStore}.
 */
public final class HybridEditHistory {
    public static final int RAM_LIMIT = 10;
    public static final int DISK_LIMIT = 50;

    private final EditHistoryStore store;
    private final Deque<String> ram = new ArrayDeque<>();

    public HybridEditHistory(EditHistoryStore store) {
        this.store = store;
    }

    public void push(long projectId, String payload) {
        if (payload == null) return;
        ram.push(payload);
        while (ram.size() > RAM_LIMIT) ram.removeLast();
        store.push(projectId, payload);
    }

    public List<String> recent(long projectId, int limit) {
        List<String> combined = new ArrayList<>(ram);
        for (String payload : store.recent(projectId, DISK_LIMIT)) {
            if (!combined.contains(payload)) combined.add(payload);
        }
        while (combined.size() > DISK_LIMIT) combined.remove(combined.size() - 1);
        if (limit > 0 && combined.size() > limit) {
            return new ArrayList<>(combined.subList(0, limit));
        }
        return combined;
    }

    public void clear(long projectId) {
        ram.clear();
        store.clear(projectId);
    }

    public int ramSize() {
        return ram.size();
    }
}
