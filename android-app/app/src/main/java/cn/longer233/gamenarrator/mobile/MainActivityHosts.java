package cn.longer233.gamenarrator.mobile;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@UnstableApi
public final class MainActivityHosts {
    private final MainActivity activity;

    public final DialogController.ExportHost exportHost;
    public final DialogController.ProjectHost projectHost;
    public final DialogController.CompilationHost compilationHost;
    public final DialogController.PlatformImportHost platformImportHost;
    public final DialogController.SettingsHost settingsHost;
    public final DialogController.ShotSearchHost shotSearchHost;
    public final DialogController.SettingsPageHost settingsPageHost;
    public final DialogController.AssetLibraryHost assetLibraryHost;
    public final DialogController.PipelineHost pipelineHost;
    public final DialogController.HomeHost homeHost;
    public final DialogController.RevisionHost revisionHost;
    public final DialogController.TrackHost trackHost;
    public final DialogController.SubtitleHost subtitleHost;
    public final DialogController.EditingHost editingHost;
    public final DialogController.KeyframeHost keyframeHost;
    public final DialogController.PlacementHost placementHost;
    public final DialogController.TemplateHost templateHost;
    public final DialogController.StoryboardHost storyboardHost;
    public final DialogController.NarrationHost narrationHost;
    public final DialogController.EditorHost editorHost;

    public MainActivityHosts(MainActivity activity) {
        this.activity = activity;
        exportHost = new DialogController.ExportHost() {
            @Override public List<MobileExportStore.ExportJobInfo> exportJobs() { return activity.projectStore.listExports(); }
            @Override public long activeExportJobId() { return activity.exportController.activeJobId(); }
            @Override public void cancelActiveExport() { activity.cancelActiveExport(); }
            @Override public void startProjectExport() { activity.exportProject(); }
            @Override public void startExport(ExportController.Preset preset) { activity.startExport(preset); }
            @Override public void showExportFile(File file) { activity.showExportActions(file); }
            @Override public void showErrorDialog(String title, String message) { activity.showError(title, message); }
            @Override public String exportStatusLabel(String status) { return activity.statusLabel(status); }
        };
        projectHost = new DialogController.ProjectHost() {
            @Override public long activeProjectId() { return activity.projectStore.activeProjectId(); }
            @Override public boolean exportActiveForProject(long projectId) {
                return projectId == activity.projectStore.activeProjectId() && activity.exportController.isActive();
            }
            @Override public void renameProject(long id, String name) {
                activity.projectStore.renameProject(id, name);
                activity.showHome();
            }
            @Override public void duplicateProject(long id) {
                activity.projectStore.duplicateProject(id);
                activity.showHome();
                Toast.makeText(activity, "项目副本已创建", Toast.LENGTH_LONG).show();
            }
            @Override public void archiveProject(long id) {
                activity.projectStore.archiveProject(id);
                activity.showHome();
            }
            @Override public void showErrorDialog(String title, String message) { activity.showError(title, message); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public String activeProjectName() { return activity.projectStore.activeProjectName(); }
            @Override public void renameRevision(long id, String name) {
                activity.projectStore.renameRevision(id, name);
                activity.openRevisionPanel();
            }
            @Override public void forkRevision(long id, String name) {
                activity.projectStore.forkRevision(id, name);
                activity.clips.clear();
                activity.clips.addAll(activity.projectStore.loadClips());
                activity.projects.clearHistory();
                activity.selected = activity.clips.isEmpty() ? -1 : 0;
                activity.showEditor();
                activity.status.setText("已从历史版本建立独立项目分支。");
            }
            @Override public void restoreRevision(ProjectRepository.RevisionInfo revision) {
                activity.restoreRevision(revision);
            }
        };
        compilationHost = new DialogController.CompilationHost() {
            @Override public List<ProjectRepository.CompilationInfo> listCompilations() { return activity.projectStore.listCompilations(); }
            @Override public List<ProjectRepository.CompilationItemInfo> listCompilationItems(long compilationId) { return activity.projectStore.listCompilationItems(compilationId); }
            @Override public long createCompilation(String name) { return activity.projectStore.createCompilation(name); }
            @Override public void addCompilationItem(long compilationId, String clipKey) { activity.projectStore.addCompilationItem(compilationId, clipKey); }
            @Override public void moveCompilationItem(long compilationId, long itemId, int delta) { activity.projectStore.moveCompilationItem(compilationId, itemId, delta); }
            @Override public void removeCompilationItem(long itemId) { activity.projectStore.removeCompilationItem(itemId); }
            @Override public void deleteCompilation(long compilationId) { activity.projectStore.deleteCompilation(compilationId); }
            @Override public void refreshCompilationsPage() { activity.showCompilations(); }
            @Override public void showCompilationsPage() { activity.showCompilations(); }
            @Override public void showHomePage() { activity.showHome(); }
            @Override public void openCompilationPage(ProjectRepository.CompilationInfo compilation) { activity.openCompilation(compilation); }
            @Override public void materializeCompilation(ProjectRepository.CompilationInfo compilation,
                                                         List<ProjectRepository.CompilationItemInfo> items) {
                activity.materializeCompilation(compilation, items);
            }
            @Override public void setStatus(String text) { if (activity.status != null) activity.status.setText(text); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public String formatDuration(long ms) { return activity.format(ms); }
        };
        platformImportHost = new DialogController.PlatformImportHost() {
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public void showHomePage() { activity.showHome(); }
            @Override public void startRemoteImport(String url, String format, boolean createProject) {
                activity.startRemoteImport(url, format, createProject);
            }
            @Override public void launchCookieFileOpen() { activity.launchCookieFileOpen(); }
            @Override public String cookieSessionFor(String url) { return activity.cookieSessionFor(url); }
        };
        settingsHost = new DialogController.SettingsHost() {
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public void showHomePage() { activity.showHome(); }
            @Override public String engineReport() { return activity.buildEngineReport(); }
            @Override public int logCount() { return StructuredLog.count(activity.runtimeLogFile()); }
            @Override public String logTail(int lines) { return activity.readLogTail(activity.runtimeLogFile(), lines); }
            @Override public void exportStructuredLog() { activity.exportStructuredLog(); }
            @Override public void confirmClearStructuredLog() { activity.confirmClearStructuredLog(); }
            @Override public void showInstalledVoicesPage() { activity.showInstalledVoices(); }
            @Override public void showGuidePage() { activity.showGuide(); }
            @Override public void showReleaseNotesPage() { activity.showReleaseNotes(); }
            @Override public void requestMicPermission(Runnable action) { activity.requestMicPermission(action); }
            @Override public void launchWhisperWavOpen(DialogController.WhisperResult callback) {
                activity.launchWhisperWavOpen(callback);
            }
            @Override public void launchVisionImageOpen(DialogController.ImageResult callback) {
                activity.launchVisionImageOpen(callback);
            }
            @Override public void showAiSettingsPage() { activity.showAiSettings(); }
        };
        shotSearchHost = new DialogController.ShotSearchHost() {
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public void showHomePage() { activity.showHome(); }
            @Override public List<TimelineClip> projectClips() { return activity.projectStore.loadClips(); }
            @Override public void openShot(TimelineClip clip) { activity.openShotFromSearch(clip); }
            @Override public void exportShot(TimelineClip clip) { activity.exportSingleClip(clip); }
        };
        settingsPageHost = new DialogController.SettingsPageHost() {
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public void showHomePage() { activity.showHome(); }
            @Override public boolean ttsReady() { return activity.ttsReady; }
            @Override public void shareDiagnostics(String report) { activity.shareDiagnostics(report); }
            @Override public void clearCacheThenRefresh() {
                long bytes = MobileDiagnostics.clearCache(activity);
                Toast.makeText(activity, "已清理 " + MobileDiagnostics.formatBytes(bytes), Toast.LENGTH_LONG).show();
                activity.showSettings();
            }
            @Override public void beginProjectArchive() { activity.beginProjectArchive(); }
            @Override public void launchProjectArchiveOpen() {
                activity.projectArchiveOpen.launch(new String[]{"application/json", "text/plain"});
            }
            @Override public List<ProjectRepository.ProjectInfo> archivedProjects() { return activity.projectStore.listArchivedProjects(); }
            @Override public void restoreProject(long id) {
                activity.projectStore.restoreProject(id);
                activity.showSettings();
            }
            @Override public void permanentlyDeleteProject(ProjectRepository.ProjectInfo project) {
                activity.confirmPermanentDelete(project);
            }
            @Override public void showSettingsPage() { activity.showSettings(); }
        };
        assetLibraryHost = new DialogController.AssetLibraryHost() {
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public void showHomePage() { activity.showHome(); }
            @Override public void launchAssetPicker() {
                activity.assetPicker.launch(new String[]{"image/*", "video/*", "audio/*"});
            }
            @Override public List<MobileAssetStore.AssetInfo> listAssets() { return activity.projectStore.listAssets(); }
            @Override public void loadAssetPreview(MobileAssetStore.AssetInfo asset, ImageView target) {
                activity.loadAssetPreview(asset, target);
            }
            @Override public void previewAsset(MobileAssetStore.AssetInfo asset) { activity.previewAsset(asset); }
            @Override public void editAssetTags(MobileAssetStore.AssetInfo asset) { activity.editAssetTags(asset); }
            @Override public void confirmRemoveAsset(MobileAssetStore.AssetInfo asset) {
                activity.confirmRemoveAsset(asset);
            }
            @Override public void refreshAssetLibrary(String query) { activity.showAssetLibrary(query); }
            @Override public String activeProjectName() { return activity.projectStore.activeProjectName(); }
        };
        pipelineHost = new DialogController.PipelineHost() {
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public void showHomePage() { activity.showHome(); }
            @Override public List<ProjectRepository.ProjectInfo> listProjects() { return activity.projectStore.listProjects(); }
            @Override public String pipelineStageFor(long projectId) { return activity.projectStore.pipelineStageFor(projectId); }
            @Override public String pipelineStage() { return activity.projectStore.pipelineStage(); }
            @Override public String activeProjectStatus() { return activity.projectStore.activeProjectStatus(); }
            @Override public String statusLabel(String status) { return activity.statusLabel(status); }
            @Override public void openProject(long projectId) { activity.openProject(projectId); }
            @Override public boolean exportActive() { return activity.exportController.isActive(); }
            @Override public void cancelActiveExport() { activity.cancelActiveExport(); }
            @Override public void launchVideoPicker() { activity.picker.launch(new String[]{"video/*"}); }
            @Override public void startProjectExport() { activity.exportProject(); }
            @Override public void completeCurrentStage() { activity.completeCurrentStage(); }
            @Override public boolean autoPipelineActive() { return activity.autoPipelineActive; }
            @Override public void cancelAutoPipeline() {
                activity.autoPipelineActive = false;
                activity.textToSpeech.stop();
                activity.status.setText("自动流水线已取消。");
            }
            @Override public void runAutomaticPipeline() { activity.runAutomaticPipeline(); }
            @Override public List<TimelineClip> activeProjectClips() { return activity.clips; }
            @Override public void autoPlanEffectsAndRender() { activity.autoPlanEffectsAndRender(); }
        };
        homeHost = new DialogController.HomeHost() {
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public String versionName() { return BuildConfig.VERSION_NAME; }
            @Override public void showPipelinePage() { activity.showPipeline(); }
            @Override public void showShotSearchPage() { activity.showShotSearch(); }
            @Override public void showPlatformImportPage() { activity.showPlatformImport(); }
            @Override public void showAssetLibraryPage() { activity.showAssetLibrary(); }
            @Override public void showCompilationsPage() { activity.showCompilations(); }
            @Override public void showAiSettingsPage() { activity.showAiSettings(); }
            @Override public void showGuidePage() { activity.showGuide(); }
            @Override public void showReleaseNotesPage() { activity.showReleaseNotes(); }
            @Override public void showPublicAssetSearch() { activity.dialogs.showPublicAssetSearch(); }
            @Override public void showSourceDirectory() { activity.dialogs.showSourceDirectory(); }
            @Override public void showSettingsPage() { activity.showSettings(); }
            @Override public void createProjectAndPickVideo() {
                createProjectWithBrief("未命名项目 " + new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new java.util.Date()),
                        "ACTION", "ANIME_THEATER", "FULL_VIDEO", 0, "", "", true, true);
            }
            @Override public void createProjectWithBrief(String name, String gameCategory, String commentaryStyle,
                                                        String editingScope, int targetDurationSeconds, String brief,
                                                        String terminologyGlossary, boolean storyboardReviewEnabled,
                                                        boolean automaticGenerationEnabled) {
                activity.projectStore.createProject(name == null || name.isBlank() ? "未命名项目" : name.trim());
                try {
                    activity.projectStore.updateTaskBrief(new ProjectRepository.TaskBrief(gameCategory, commentaryStyle,
                            editingScope, targetDurationSeconds, brief, terminologyGlossary,
                            storyboardReviewEnabled, automaticGenerationEnabled));
                } catch (Exception ignored) { }
                activity.logEvent("project", "创建本地项目：" + activity.projectStore.activeProjectName());
                activity.clips.clear();
                activity.projects.clearHistory();
                activity.selected = -1;
                activity.showEditor();
                activity.picker.launch(new String[]{"video/*"});
            }
            @Override public List<ProjectRepository.ProjectInfo> listProjects() { return activity.projectStore.listProjects(); }
            @Override public String statusLabel(String status) { return activity.statusLabel(status); }
            @Override public void openProject(long id) { activity.openProject(id); }
            @Override public void showProjectActions(ProjectRepository.ProjectInfo project) {
                activity.showProjectActions(project);
            }
        };
        revisionHost = new DialogController.RevisionHost() {
            @Override public List<ProjectRepository.RevisionInfo> listRevisions() { return activity.projectStore.listRevisions(); }
            @Override public List<TimelineClip> currentClips() { return activity.clips; }
            @Override public List<TimelineClip> loadRevisionClips(long id) { return activity.projectStore.loadRevision(id); }
            @Override public void restoreRevision(ProjectRepository.RevisionInfo revision) {
                activity.restoreRevision(revision);
            }
            @Override public void showRevisionActions(ProjectRepository.RevisionInfo revision) {
                activity.showRevisionActions(revision);
            }
            @Override public void showRevisionComparePicker() { activity.showRevisionComparePicker(); }
            @Override public void applyRevisionMerge(ProjectRepository.RevisionInfo revision, RevisionMerge.Mode mode) {
                activity.applyRevisionMerge(revision, mode);
            }
            @Override public String formatDuration(long ms) { return activity.format(ms); }
            @Override public void logEvent(String source, String message) { activity.logEvent(source, message); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
        };
        trackHost = new DialogController.TrackHost() {
            @Override public TimelineClip currentClip() { return activity.current(); }
            @Override public void beginEdit(String description) { activity.pushHistory(description); }
            @Override public void commitEdit(String reason) { activity.persistProject(reason); }
            @Override public void renderTimeline() { activity.renderTimeline(); }
            @Override public void setStatus(String text) { if (activity.status != null) activity.status.setText(text); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public ProjectRepository.TrackState trackState(String type) { return activity.projectStore.trackState(type); }
            @Override public void updateTrackState(String type, boolean muted, boolean solo) {
                activity.projectStore.updateTrackState(type, muted, solo);
            }
            @Override public void refreshTrackControls() { activity.showTrackControls(); }
        };
        subtitleHost = new DialogController.SubtitleHost() {
            @Override public List<ProjectRepository.SubtitleCueInfo> listSubtitleCues() { return activity.projectStore.listSubtitleCues(); }
            @Override public void replaceSubtitleCues(List<SubtitleFileCodec.Cue> cues) { activity.projectStore.replaceSubtitleCues(cues); }
            @Override public void clearSubtitleCues() { activity.projectStore.clearSubtitleCues(); }
            @Override public void beginEdit(String description) { activity.pushHistory(description); }
            @Override public void commitEdit(String reason) { activity.persistProject(reason); }
            @Override public void renderTimeline() { activity.renderTimeline(); }
            @Override public void setStatus(String text) { if (activity.status != null) activity.status.setText(text); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public void showErrorDialog(String title, String message) { activity.showError(title, message); }
            @Override public String formatDuration(long ms) { return activity.format(ms); }
            @Override public String activeProjectName() { return activity.projectStore.activeProjectName(); }
            @Override public void launchSubtitleFileOpen() {
                activity.subtitleFileOpen.launch(new String[]{"application/x-subrip", "text/plain", "application/octet-stream"});
            }
            @Override public void launchSubtitleFileCreate(String safeName) { activity.subtitleFileCreate.launch(safeName); }
            @Override public void setPendingSubtitleFile(String content) { activity.pendingSubtitleFile = content; }
            @Override public void refreshSubtitleFiles() { activity.showSubtitleFiles(); }
        };
        editingHost = new DialogController.EditingHost() {
            @Override public TimelineClip currentClip() { return activity.current(); }
            @Override public void beginEdit(String description) { activity.pushHistory(description); }
            @Override public void commitEdit(String reason) { activity.persistProject(reason); }
            @Override public void renderTimeline() { activity.renderTimeline(); }
            @Override public void setStatus(String text) { if (activity.status != null) activity.status.setText(text); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public void showErrorDialog(String title, String message) { activity.showError(title, message); }
            @Override public ProjectRepository.ClipAudioConfig clipAudioConfig(String clipKey) { return activity.projectStore.clipAudioConfig(clipKey); }
            @Override public void saveClipAudioConfig(String clipKey, float volume, long in, long out) {
                activity.projectStore.saveClipAudioConfig(clipKey, volume, in, out);
            }
            @Override public ProjectRepository.ClipVisualConfig clipVisualConfig(String clipKey) { return activity.projectStore.clipVisualConfig(clipKey); }
            @Override public void saveClipVisualConfig(String clipKey, float brightness, float contrast, float saturation,
                                                       float temperature, float hue, float scale, float rotation) {
                activity.projectStore.saveClipVisualConfig(clipKey, brightness, contrast, saturation, temperature, hue, scale, rotation);
            }
            @Override public void saveClipLut(String clipKey, String lutPath) {
                ProjectRepository.ClipVisualConfig value = activity.projectStore.clipVisualConfig(clipKey);
                activity.projectStore.saveClipVisualConfig(clipKey, value.brightness(), value.contrast(), value.saturation(),
                        value.temperature(), value.hue(), value.scale(), value.rotation(), lutPath);
            }
            @Override public void launchLutFileOpen(String clipKey) { activity.pendingLutClipKey = clipKey; activity.lutFileOpen.launch(new String[]{"application/octet-stream", "text/plain", "*/*"}); }
            @Override public void applyPreviewEffects() { activity.applyCurrentPreviewEffects(); }
            @Override public void seekTo(long positionMs) { activity.player.seekTo(positionMs); }
            @Override public void exportSingleClip(TimelineClip clip) { activity.exportSingleClip(clip); }
            @Override public void deriveCoverAsset(TimelineClip clip) { activity.deriveCoverAsset(clip); }
            @Override public ExecutorService thumbnails() { return activity.thumbnails; }
            @Override public Handler handler() { return activity.handler; }
            @Override public long valueOf(EditText field) { return activity.value(field); }
            @Override public float floatValueOf(EditText field) { return activity.floatValue(field); }
        };
        keyframeHost = new DialogController.KeyframeHost() {
            @Override public TimelineClip currentClip() { return activity.current(); }
            @Override public long playbackPosition() { return activity.player.getCurrentPosition(); }
            @Override public String formatDuration(long ms) { return activity.format(ms); }
            @Override public void beginEdit(String description) { activity.pushHistory(description); }
            @Override public void commitEdit(String reason) { activity.persistProject(reason); }
            @Override public void renderTimeline() { activity.renderTimeline(); }
            @Override public void setStatus(String text) { if (activity.status != null) activity.status.setText(text); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public void showErrorDialog(String title, String message) { activity.showError(title, message); }
            @Override public void saveKeyframe(String clipKey, String property, long timeMs, float value) {
                activity.projectStore.saveKeyframe(clipKey, property, timeMs, value);
            }
            @Override public void saveKeyframe(String clipKey, String property, long timeMs, float value, String easing) {
                activity.projectStore.saveKeyframe(clipKey, property, timeMs, value, easing);
            }
            @Override public List<ProjectRepository.KeyframeInfo> listKeyframes(String clipKey, String property) {
                return activity.projectStore.listKeyframes(clipKey, property);
            }
            @Override public void clearKeyframes(String clipKey, String property) {
                activity.projectStore.clearKeyframes(clipKey, property);
            }
            @Override public void replaceKeyframes(String clipKey, String property,
                                                   List<ProjectRepository.KeyframeInfo> frames) {
                activity.projectStore.replaceKeyframes(clipKey, property, frames);
            }
            @Override public float floatValueOf(EditText field) { return activity.floatValue(field); }
            @Override public long valueOf(EditText field) { return activity.value(field); }
        };
        placementHost = new DialogController.PlacementHost() {
            @Override public TimelineClip currentClip() { return activity.current(); }
            @Override public long currentClipDurationOrMax() {
                TimelineClip clip = activity.current();
                return clip == null ? Long.MAX_VALUE : clip.durationMs();
            }
            @Override public List<MobileAssetStore.PlacementInfo> listPlacements() { return activity.projectStore.listPlacements(); }
            @Override public List<MobileAssetStore.AssetInfo> listAssets() { return activity.projectStore.listAssets(); }
            @Override public void placeAsset(String clipKey, long assetId, String role) {
                activity.projectStore.placeAsset(clipKey, assetId, role);
            }
            @Override public void updatePlacementMix(long id, float volume, long in, long out) {
                activity.projectStore.updatePlacementMix(id, volume, in, out);
            }
            @Override public void updatePlacementVisual(long id, float x, float y, float scale) {
                activity.projectStore.updatePlacementVisual(id, x, y, scale);
            }
            @Override public void removePlacement(long id) { activity.projectStore.removePlacement(id); }
            @Override public void beginEdit(String description) { activity.pushHistory(description); }
            @Override public void commitEdit(String reason) { activity.persistProject(reason); }
            @Override public void renderTimeline() { activity.renderTimeline(); }
            @Override public void setStatus(String text) { if (activity.status != null) activity.status.setText(text); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public void showAssetLibraryPage() { activity.showAssetLibrary(); }
            @Override public long valueOf(EditText field) { return activity.value(field); }
            @Override public float floatValueOf(EditText field) { return activity.floatValue(field); }
        };
        templateHost = new DialogController.TemplateHost() {
            @Override public TimelineClip currentClip() { return activity.current(); }
            @Override public List<ProjectRepository.EffectTemplateInfo> listEffectTemplates() {
                return activity.projectStore.listEffectTemplates();
            }
            @Override public void deleteEffectTemplate(long id) { activity.projectStore.deleteEffectTemplate(id); }
            @Override public void createEffectTemplate(String name, String cue) {
                activity.projectStore.createEffectTemplate(name, cue);
            }
            @Override public void beginEdit(String description) { activity.pushHistory(description); }
            @Override public void commitEdit(String reason) { activity.persistProject(reason); }
            @Override public void renderTimeline() { activity.renderTimeline(); }
            @Override public void setStatus(String text) { if (activity.status != null) activity.status.setText(text); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public void showErrorDialog(String title, String message) { activity.showError(title, message); }
            @Override public void logEvent(String source, String message) { activity.logEvent(source, message); }
            @Override public void refreshTemplates() { activity.showEffectTemplates(); }
            @Override public void exportTemplates() { activity.beginTemplateExport(); }
            @Override public void importTemplates() { activity.importTemplates(); }
        };
        storyboardHost = new DialogController.StoryboardHost() {
            @Override public TimelineClip currentClip() { return activity.current(); }
            @Override public List<TimelineClip> allClips() { return activity.clips; }
            @Override public void beginEdit(String description) { activity.pushHistory(description); }
            @Override public void commitEdit(String reason) { activity.persistProject(reason); }
            @Override public void renderTimeline() { activity.renderTimeline(); }
            @Override public void setStatus(String text) { if (activity.status != null) activity.status.setText(text); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
            @Override public ProjectRepository.ClipReviewInfo clipReview(String clipKey) { return activity.projectStore.clipReview(clipKey); }
            @Override public void reviewClip(String clipKey, String status, String note) {
                activity.projectStore.reviewClip(clipKey, status, note);
            }
            @Override public void selectClip(int index) { activity.selectClip(index); }
            @Override public void openStoryboardPanel() { activity.openSubtitlePanel(); }
        };
        narrationHost = new DialogController.NarrationHost() {
            @Override public TimelineClip currentClip() { return activity.current(); }
            @Override public boolean ttsReady() { return activity.ttsReady; }
            @Override public List<String> availableOfflineVoiceNames() {
                List<android.speech.tts.Voice> voices = new ArrayList<>();
                if (activity.textToSpeech.getVoices() != null) {
                    for (android.speech.tts.Voice voice : activity.textToSpeech.getVoices()) {
                        if (!voice.isNetworkConnectionRequired()) voices.add(voice);
                    }
                }
                voices.sort(java.util.Comparator.comparing(android.speech.tts.Voice::getName));
                List<String> names = new ArrayList<>();
                for (android.speech.tts.Voice voice : voices) names.add(voice.getName());
                return names;
            }
            @Override public String voiceDisplayName(String voiceName) {
                if (activity.textToSpeech.getVoices() != null) {
                    for (android.speech.tts.Voice voice : activity.textToSpeech.getVoices()) {
                        if (voice.getName().equals(voiceName)) {
                            return voice.getLocale().getDisplayName(Locale.CHINA) + " · " + voice.getName();
                        }
                    }
                }
                return voiceName;
            }
            @Override public String configuredVoiceName(String clipKey) {
                return activity.projectStore.voiceConfig(clipKey).voiceName();
            }
            @Override public float configuredSpeed(String clipKey) {
                return activity.projectStore.voiceConfig(clipKey).speed();
            }
            @Override public float configuredPitch(String clipKey) {
                return activity.projectStore.voiceConfig(clipKey).pitch();
            }
            @Override public void saveVoiceConfig(String clipKey, String voiceName, float speed, float pitch) {
                activity.projectStore.saveVoiceConfig(clipKey, voiceName, speed, pitch);
            }
            @Override public void startNarration(TimelineClip clip, String voiceName, float speed, float pitch) {
                android.speech.tts.Voice target = null;
                if (activity.textToSpeech.getVoices() != null) {
                    for (android.speech.tts.Voice voice : activity.textToSpeech.getVoices()) {
                        if (voice.getName().equals(voiceName)) { target = voice; break; }
                    }
                }
                if (target != null) activity.generateNarration(clip, target, speed, pitch);
            }
            @Override public void showErrorDialog(String title, String message) { activity.showError(title, message); }
            @Override public void unavailable(String message) { activity.unavailable(message); }
        };
        editorHost = new DialogController.EditorHost() {
            @Override public ViewGroup pageContainer() { return activity.shell; }
            @Override public void showHomePage() { activity.showHome(); }
            @Override public String activeProjectName() { return activity.projectStore.activeProjectName(); }
            @Override public void openRevisionPanel() { activity.openRevisionPanel(); }
            @Override public void showExportJobs() { activity.dialogs.showExportJobs(); }
            @Override public void showPipelinePanel() { activity.showPipelinePanel(); }
            @Override public void startProjectExport() { activity.exportProject(); }
            @Override public ExoPlayer player() { return activity.player; }
            @Override public void playOrPause() { activity.playback.playOrPause(); }
            @Override public void stepFrame(int direction) { activity.stepFrame(direction); }
            @Override public void markBoundary(boolean inPoint) { activity.markBoundary(inPoint); }
            @Override public void showPlaybackSpeed() { activity.showPlaybackSpeed(); }
            @Override public void launchVideoPicker() { activity.picker.launch(new String[]{"video/*"}); }
            @Override public int timelineZoom() { return activity.timelineViewController.timelineZoom(); }
            @Override public void setTimelineZoom(int value) { activity.timelineViewController.setTimelineZoom(value); }
            @Override public void renderTimeline() { activity.renderTimeline(); }
            @Override public boolean handleTimelineDrag(DragEvent event, ViewGroup timeline) {
                return activity.timelineViewController.handleTimelineDrag(event, timeline);
            }
            @Override public void undo() { activity.undo(); }
            @Override public void redo() { activity.redo(); }
            @Override public void split() { activity.split(); }
            @Override public void mergeRight() { activity.mergeRight(); }
            @Override public void showTrackAssignment() { activity.showTrackAssignment(); }
            @Override public void confirmDelete() { activity.confirmDelete(); }
            @Override public void openClipPanel() { activity.openClipPanel(); }
            @Override public void showClipVisualAdjustments() { activity.showClipVisualAdjustments(); }
            @Override public void showKeyframes() { activity.showKeyframes(); }
            @Override public void showPositionKeyframes() { activity.showPositionKeyframes(); }
            @Override public void showOpacityKeyframes() { activity.showOpacityKeyframes(); }
            @Override public void showVolumeKeyframes() { activity.showVolumeKeyframes(); }
            @Override public void showKeyframeCurveEditor() { activity.showKeyframeCurveEditor(); }
            @Override public void showEffectTemplates() { activity.showEffectTemplates(); }
            @Override public void openSubtitlePanel() { activity.openSubtitlePanel(); }
            @Override public void reviewCurrentClip() { activity.reviewCurrentClip(); }
            @Override public void showScriptQuality() { activity.showScriptQuality(); }
            @Override public void showSubtitleFiles() { activity.showSubtitleFiles(); }
            @Override public void openAssetPlacement() { activity.openAssetPlacement(); }
            @Override public void addCurrentClipToCompilation() { activity.addCurrentClipToCompilation(); }
            @Override public void synthesizeNarration() { activity.synthesizeNarration(); }
            @Override public void showTrackControls() { activity.showTrackControls(); }
            @Override public int screenWidthDp() { return activity.getResources().getConfiguration().screenWidthDp; }
            @Override public boolean landscape() { return activity.getResources().getConfiguration().orientation == 2; }
            @Override public void attachEditorViews(LinearLayout timelineView, TextView rulerView,
                                                    TextView projectSummaryView, TextView emptyTimelineView,
                                                    WaveformView waveform, LinearLayout subtitleLane,
                                                    LinearLayout narrationLane, LinearLayout effectLane,
                                                    LinearLayout assetLane, TextView statusView) {
                activity.timeline = timelineView;
                activity.ruler = rulerView;
                activity.projectSummary = projectSummaryView;
                activity.emptyTimeline = emptyTimelineView;
                activity.waveformView = waveform;
                activity.subtitleTrack = subtitleLane;
                activity.narrationTrack = narrationLane;
                activity.effectTrack = effectLane;
                activity.assetTrack = assetLane;
                activity.status = statusView;
            }
            @Override public void selectCurrentIfValid() {
                if (activity.selected >= 0 && activity.selected < activity.clips.size()) activity.selectClip(activity.selected);
            }
        };
    }

    public void attachTo(DialogController dialogs) {
        dialogs.attachSettingsPageHost(settingsPageHost);
        dialogs.attachAssetLibraryHost(assetLibraryHost);
        dialogs.attachPipelineHost(pipelineHost);
        dialogs.attachHomeHost(homeHost);
        dialogs.attachRevisionHost(revisionHost);
        dialogs.attachTrackHost(trackHost);
        dialogs.attachSubtitleHost(subtitleHost);
        dialogs.attachEditingHost(editingHost);
        dialogs.attachKeyframeHost(keyframeHost);
        dialogs.attachPlacementHost(placementHost);
        dialogs.attachTemplateHost(templateHost);
        dialogs.attachStoryboardHost(storyboardHost);
        dialogs.attachNarrationHost(narrationHost);
        dialogs.attachEditorHost(editorHost);
    }
}
