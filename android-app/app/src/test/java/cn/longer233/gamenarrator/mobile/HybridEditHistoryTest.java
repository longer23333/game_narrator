package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class HybridEditHistoryTest {
    @Test public void keepsTenInRamAndFiftyOnDisk() {
        FakeStore store = new FakeStore();
        HybridEditHistory history = new HybridEditHistory(store);
        for (int i = 1; i <= 60; i++) history.push(7, "snapshot-" + i);
        assertEquals(10, history.ramSize());
        assertEquals(50, store.count(7));
        List<String> recent = history.recent(7, 50);
        assertEquals(50, recent.size());
        assertEquals("snapshot-60", recent.get(0));
        assertEquals("snapshot-11", recent.get(recent.size() - 1));
    }

    @Test public void recentMergesRamMirrorAndDiskWithoutDuplicates() {
        FakeStore store = new FakeStore();
        HybridEditHistory history = new HybridEditHistory(store);
        history.push(3, "a");
        history.push(3, "b");
        List<String> recent = history.recent(3, 10);
        assertEquals(2, recent.size());
        assertEquals("b", recent.get(0));
        assertEquals("a", recent.get(1));
    }

    @Test public void clearRemovesRamAndDisk() {
        FakeStore store = new FakeStore();
        HybridEditHistory history = new HybridEditHistory(store);
        history.push(5, "one");
        history.clear(5);
        assertEquals(0, history.ramSize());
        assertEquals(0, store.count(5));
        assertTrue(history.recent(5, 10).isEmpty());
    }

    private static final class FakeStore implements EditHistoryStore {
        private final List<String> rows = new ArrayList<>();

        @Override public void push(long projectId, String payload) {
            rows.add(0, payload);
            while (rows.size() > SqliteEditHistoryStore.MAX_ENTRIES) rows.remove(rows.size() - 1);
        }

        @Override public List<String> recent(long projectId, int limit) {
            return new ArrayList<>(rows.subList(0, Math.min(rows.size(), Math.max(1, limit))));
        }

        @Override public void clear(long projectId) {
            rows.clear();
        }

        @Override public int count(long projectId) {
            return rows.size();
        }
    }
}
