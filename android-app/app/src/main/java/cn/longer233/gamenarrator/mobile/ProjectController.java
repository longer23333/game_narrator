package cn.longer233.gamenarrator.mobile;

import java.io.File;
import java.util.List;

/**
 * Owns the project-scoped edit flow: snapshot history, persistence, and
 * interrupted-export recovery. The activity keeps rendering and navigation.
 */
public final class ProjectController {
    private final ProjectStore store;
    private final List<TimelineClip> clips;
    private final TimelineController timeline;

    public ProjectController(ProjectStore store, List<TimelineClip> clips, TimelineController timeline) {
        this.store = store;
        this.clips = clips;
        this.timeline = timeline;
    }

    public void beginEdit(String description) {
        timeline.pushSnapshot(description);
    }

    public void commitEdit(String reason) {
        store.saveClips(clips, reason);
        timeline.captureSnapshot();
    }

    public void saveOnly(String reason) {
        store.saveClips(clips, reason);
    }

    public boolean canUndo() { return timeline.canUndo(); }
    public boolean canRedo() { return timeline.canRedo(); }

    public boolean undo() {
        return timeline.undo();
    }

    public boolean redo() {
        return timeline.redo();
    }

    public void clearHistory() {
        timeline.clearHistory();
    }

    public List<TimelineClip> loadClips() {
        return store.loadClips();
    }

    public String buildProjectArchive() throws Exception {
        return store.exportActiveProject();
    }

    public void restoreProjectArchive(String json) throws Exception {
        store.importProject(json);
        clips.clear();
        clips.addAll(store.loadClips());
        timeline.clearHistory();
    }

    /**
     * Deletes partial output files left by exports that were interrupted while
     * the app was not running. Only files inside the app-owned movies directory
     * are considered; completed or user-cancelled jobs are never touched.
     */
    public int recoverInterruptedExports(File moviesDir) {
        int cleaned = 0;
        for (MobileExportStore.ExportJobInfo job : store.listExports()) {
            if (job == null || job.outputPath() == null || job.outputPath().isBlank()) continue;
            File output = new File(job.outputPath());
            if (!ExportRecoveryPolicy.shouldCleanOutput(job.status(), job.error(), output, moviesDir)) continue;
            if (output.isFile() && output.delete()) cleaned++;
        }
        return cleaned;
    }
}
