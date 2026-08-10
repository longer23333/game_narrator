package cn.longer233.gamenarrator.mobile;

import java.util.List;

/**
 * Narrow persistence surface used by project-scoped controllers. The SQLite
 * store implements this so unit tests can exercise edit and recovery flows
 * without an Android runtime.
 */
public interface ProjectStore {
    void saveClips(List<TimelineClip> clips, String reason);

    List<MobileExportStore.ExportJobInfo> listExports();

    List<TimelineClip> loadClips();

    String exportActiveProject() throws Exception;

    long importProject(String json) throws Exception;
}
