package cn.longer233.gamenarrator.mobile;

import java.util.List;

/**
 * Persistence contract for project edit-history payloads.
 */
public interface EditHistoryStore {
    void push(long projectId, String payload);

    List<String> recent(long projectId, int limit);

    void clear(long projectId);

    int count(long projectId);
}
