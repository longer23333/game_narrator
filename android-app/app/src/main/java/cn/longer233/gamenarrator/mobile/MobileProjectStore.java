package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import java.util.List;

/**
 * Assembles the mobile persistence stores and delegates the public project API
 * to the store that owns each responsibility.
 */
public final class MobileProjectStore implements ProjectStore {
    private final MobileDatabase database;
    private final ProjectRepository projects;
    private final MobileAssetStore assets;
    private final MobileExportStore exports;

    public MobileProjectStore(Context context) {
        database = new MobileDatabase(context);
        assets = new MobileAssetStore(database);
        exports = new MobileExportStore(database);
        projects = new ProjectRepository(database, assets, exports);
    }

    ProjectRepository projects() { return projects; }
    MobileDatabase database() { return database; }

    public long activeProjectId() { return projects.activeProjectId(); }

    public long createProject(String name) { return projects.createProject(name); }

    public void selectProject(long id) { projects.selectProject(id); }

    public String pipelineStage() { return projects.pipelineStage(); }

    public String pipelineStageFor(long projectId) { return projects.pipelineStageFor(projectId); }

    public void setPipelineStage(String stage) { projects.setPipelineStage(stage); }

    public String activeProjectStatus() { return projects.activeProjectStatus(); }

    public String activeProjectName() { return projects.activeProjectName(); }

    public List<ProjectRepository.ProjectInfo> listProjects() { return projects.listProjects(); }

    public List<ProjectRepository.ProjectInfo> listArchivedProjects() { return projects.listArchivedProjects(); }

    public ProjectRepository.TaskBrief taskBrief() { return projects.taskBrief(); }

    public void updateTaskBrief(ProjectRepository.TaskBrief brief) { projects.updateTaskBrief(brief); }

    public void renameProject(long id, String name) { projects.renameProject(id, name); }

    public long duplicateProject(long id) { return projects.duplicateProject(id); }

    public void archiveProject(long id) { projects.archiveProject(id); }

    public void restoreProject(long id) { projects.restoreProject(id); }

    public void permanentlyDeleteProject(long id) { projects.permanentlyDeleteProject(id); }

    public void updateStatus(String status) { projects.updateStatus(status); }

    public long startExport(String outputPath, String preset) {
        return exports.startExport(projects.activeProjectId(), outputPath, preset);
    }

    public void updateExport(long id, String status, int progress, String error) {
        exports.updateExport(projects.activeProjectId(), id, status, progress, error);
    }

    public List<MobileExportStore.ExportJobInfo> listExports() { return exports.listExports(projects.activeProjectId()); }

    public long addAsset(String uri, String name, String type) { return assets.addAsset(uri, name, type); }

    public List<MobileAssetStore.AssetInfo> listAssets() { return assets.listAssets(); }

    public void updateAssetTags(long id, String tags) { assets.updateAssetTags(id, tags); }

    public void deleteAsset(long id) { assets.deleteAsset(id); }

    public void placeAsset(String clipKey, long assetId, String role) {
        assets.placeAsset(projects.activeProjectId(), clipKey, assetId, role);
    }

    public List<MobileAssetStore.PlacementInfo> listPlacements() {
        return assets.listPlacements(projects.activeProjectId());
    }

    public ProjectRepository.TrackState trackState(String type) { return projects.trackState(type); }

    public void updateTrackState(String type, boolean muted, boolean solo) {
        projects.updateTrackState(type, muted, solo);
    }

    public void updatePlacementMix(long id, float volume, long fadeInMs, long fadeOutMs) {
        assets.updatePlacementMix(projects.activeProjectId(), id, volume, fadeInMs, fadeOutMs);
    }

    public void updatePlacementVisual(long id, float x, float y, float scale) {
        assets.updatePlacementVisual(projects.activeProjectId(), id, x, y, scale);
    }

    public void removePlacement(long id) { assets.removePlacement(projects.activeProjectId(), id); }

    public void removePlacementsForClip(String clipKey) {
        assets.removePlacementsForClip(projects.activeProjectId(), clipKey);
    }

    public void removePlacementsForRole(String clipKey, String role) {
        assets.removePlacementsForRole(projects.activeProjectId(), clipKey, role);
    }

    public void replacePlacements(List<ProjectSnapshot.Placement> placements) {
        assets.replacePlacements(projects.activeProjectId(), placements);
    }

    public ProjectRepository.VoiceConfig voiceConfig(String clipKey) { return projects.voiceConfig(clipKey); }

    public void saveVoiceConfig(String clipKey, String voiceName, float speed, float pitch) {
        projects.saveVoiceConfig(clipKey, voiceName, speed, pitch);
    }

    public ProjectRepository.ClipAudioConfig clipAudioConfig(String clipKey) {
        return projects.clipAudioConfig(clipKey);
    }

    public void saveClipAudioConfig(String clipKey, float volume, long fadeInMs, long fadeOutMs) {
        projects.saveClipAudioConfig(clipKey, volume, fadeInMs, fadeOutMs);
    }

    public ProjectRepository.ClipVisualConfig clipVisualConfig(String clipKey) {
        return projects.clipVisualConfig(clipKey);
    }

    public void saveClipVisualConfig(String clipKey, float brightness, float contrast, float saturation,
                                     float temperature, float hue, float scale, float rotation) {
        projects.saveClipVisualConfig(clipKey, brightness, contrast, saturation, temperature, hue, scale, rotation);
    }
    public void saveClipVisualConfig(String clipKey, float brightness, float contrast, float saturation,
                                     float temperature, float hue, float scale, float rotation, String lutPath) {
        projects.saveClipVisualConfig(clipKey, brightness, contrast, saturation, temperature, hue, scale, rotation, lutPath);
    }

    public List<ProjectRepository.KeyframeInfo> listKeyframes(String clipKey, String property) {
        return projects.listKeyframes(clipKey, property);
    }

    public void saveKeyframe(String clipKey, String property, long timeMs, float value) {
        projects.saveKeyframe(clipKey, property, timeMs, value);
    }

    public void saveKeyframe(String clipKey, String property, long timeMs, float value, String easing) {
        projects.saveKeyframe(clipKey, property, timeMs, value, easing);
    }

    public void replaceKeyframes(String clipKey, String property, List<ProjectRepository.KeyframeInfo> frames) {
        projects.replaceKeyframes(clipKey, property, frames);
    }

    public void clearKeyframes(String clipKey, String property) { projects.clearKeyframes(clipKey, property); }

    public long createEffectTemplate(String name, String cue) { return projects.createEffectTemplate(name, cue); }

    public List<ProjectRepository.EffectTemplateInfo> listEffectTemplates() {
        return projects.listEffectTemplates();
    }

    public void deleteEffectTemplate(long id) { projects.deleteEffectTemplate(id); }

    public String exportEffectTemplatesJson() throws Exception { return projects.exportEffectTemplatesJson(); }

    public int importEffectTemplates(String json) throws Exception { return projects.importEffectTemplates(json); }

    public void copyPlacements(String fromClipKey, String toClipKey) {
        assets.copyPlacements(projects.activeProjectId(), fromClipKey, toClipKey);
    }

    public long createCompilation(String name) { return projects.createCompilation(name); }

    public List<ProjectRepository.CompilationInfo> listCompilations() { return projects.listCompilations(); }

    public void addCompilationItem(long compilationId, String clipKey) {
        projects.addCompilationItem(compilationId, clipKey);
    }

    public List<ProjectRepository.CompilationItemInfo> listCompilationItems(long compilationId) {
        return projects.listCompilationItems(compilationId);
    }

    public void removeCompilationItem(long itemId) { projects.removeCompilationItem(itemId); }

    public void deleteCompilation(long id) { projects.deleteCompilation(id); }

    public void moveCompilationItem(long compilationId, long itemId, int delta) {
        projects.moveCompilationItem(compilationId, itemId, delta);
    }

    public List<TimelineClip> loadClips() { return projects.loadClips(); }

    public List<ProjectRepository.SubtitleCueInfo> listSubtitleCues() { return projects.listSubtitleCues(); }

    public ProjectRepository.ClipReviewInfo clipReview(String clipKey) { return projects.clipReview(clipKey); }

    public List<ProjectRepository.ClipReviewInfo> listClipReviews() { return projects.listClipReviews(); }

    public void reviewClip(String clipKey, String status, String note) {
        projects.reviewClip(clipKey, status, note);
    }

    public void clearClipReview(String clipKey) { projects.clearClipReview(clipKey); }

    public void replaceSubtitleCues(List<SubtitleFileCodec.Cue> cues) { projects.replaceSubtitleCues(cues); }

    public void clearSubtitleCues() { projects.clearSubtitleCues(); }

    public List<TimelineClip> loadRevision(long id) { return projects.loadRevision(id); }

    public List<ProjectRepository.RevisionInfo> listRevisions() { return projects.listRevisions(); }

    public void renameRevision(long id, String name) { projects.renameRevision(id, name); }

    public long forkRevision(long revisionId, String name) { return projects.forkRevision(revisionId, name); }

    public void saveClips(List<TimelineClip> clips, String reason) { projects.saveClips(clips, reason); }

    public String exportActiveProject() throws Exception { return projects.exportActiveProject(); }

    public long importProject(String json) throws Exception { return projects.importProject(json); }

    public void close() { database.close(); }
}
