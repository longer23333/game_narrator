package cn.longer233.gamenarrator.mobile;

import java.util.List;

/**
 * Room-backed {@link EditHistoryStore}; mirrors the SQLite implementation's
 * retention contract.
 */
public final class RoomEditHistoryStore implements EditHistoryStore {
    private final EditHistoryDao dao;

    public RoomEditHistoryStore(EditHistoryDao dao) {
        this.dao = dao;
    }

    @Override public void push(long projectId, String payload) {
        EditHistoryEntity row = new EditHistoryEntity();
        row.projectId = projectId;
        row.payload = payload;
        row.createdAt = System.currentTimeMillis();
        dao.insert(row);
        dao.trim(projectId, SqliteEditHistoryStore.MAX_ENTRIES);
    }

    @Override public List<String> recent(long projectId, int limit) {
        return dao.recentPayloads(projectId, Math.max(1, limit));
    }

    @Override public void clear(long projectId) {
        dao.clear(projectId);
    }

    @Override public int count(long projectId) {
        return dao.count(projectId);
    }
}
