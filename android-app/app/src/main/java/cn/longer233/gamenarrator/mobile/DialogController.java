package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Shared dialog and view-building helpers so the activity stays focused on
 * lifecycle and page entry points.
 */
@UnstableApi
public final class DialogController {
    private static final int SURFACE = Color.WHITE;
    private static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);
    private static final int GREEN = Color.rgb(113, 230, 108);
    private static final int DANGER = Color.rgb(255, 85, 119);

    public interface ExportHost {
        List<MobileExportStore.ExportJobInfo> exportJobs();
        long activeExportJobId();
        void cancelActiveExport();
        void startProjectExport();
        void startExport(ExportController.Preset preset);
        void showExportFile(File file);
        void showErrorDialog(String title, String message);
        String exportStatusLabel(String status);
    }

    public interface ProjectHost {
        long activeProjectId();
        boolean exportActiveForProject(long projectId);
        void renameProject(long id, String name);
        void duplicateProject(long id);
        void archiveProject(long id);
        void showErrorDialog(String title, String message);
        void unavailable(String message);
        String activeProjectName();
        void renameRevision(long id, String name);
        void forkRevision(long id, String name);
        void restoreRevision(ProjectRepository.RevisionInfo revision);
    }

    public interface CompilationHost {
        List<ProjectRepository.CompilationInfo> listCompilations();
        List<ProjectRepository.CompilationItemInfo> listCompilationItems(long compilationId);
        long createCompilation(String name);
        void addCompilationItem(long compilationId, String clipKey);
        void moveCompilationItem(long compilationId, long itemId, int delta);
        void removeCompilationItem(long itemId);
        void deleteCompilation(long compilationId);
        void refreshCompilationsPage();
        void showCompilationsPage();
        void showHomePage();
        void openCompilationPage(ProjectRepository.CompilationInfo compilation);
        void materializeCompilation(ProjectRepository.CompilationInfo compilation,
                                    List<ProjectRepository.CompilationItemInfo> items);
        void setStatus(String text);
        void unavailable(String message);
        ViewGroup pageContainer();
        String formatDuration(long ms);
    }

    public interface PlatformImportHost {
        ViewGroup pageContainer();
        void showHomePage();
        void startRemoteImport(String url, String format, boolean createProject);
        void launchCookieFileOpen();
        String cookieSessionFor(String url);
    }

    public interface SettingsHost {
        ViewGroup pageContainer();
        void showHomePage();
        String engineReport();
        int logCount();
        String logTail(int lines);
        void exportStructuredLog();
        void confirmClearStructuredLog();
        void showInstalledVoicesPage();
        void showGuidePage();
        void showReleaseNotesPage();
        void requestMicPermission(Runnable action);
        void launchWhisperWavOpen(WhisperResult callback);
        void launchVisionImageOpen(ImageResult callback);
        void showAiSettingsPage();
    }

    public interface WhisperResult {
        void onResult(String message);
    }

    public interface ImageResult {
        void onResult(String message);
    }

    public interface ShotSearchHost {
        ViewGroup pageContainer();
        void showHomePage();
        List<TimelineClip> projectClips();
        void openShot(TimelineClip clip);
        void exportShot(TimelineClip clip);
    }

    public interface SettingsPageHost {
        ViewGroup pageContainer();
        void showHomePage();
        boolean ttsReady();
        void shareDiagnostics(String report);
        void clearCacheThenRefresh();
        void beginProjectArchive();
        void launchProjectArchiveOpen();
        List<ProjectRepository.ProjectInfo> archivedProjects();
        void restoreProject(long id);
        void permanentlyDeleteProject(ProjectRepository.ProjectInfo project);
        void showSettingsPage();
    }

    public interface AssetLibraryHost {
        ViewGroup pageContainer();
        void showHomePage();
        void launchAssetPicker();
        List<MobileAssetStore.AssetInfo> listAssets();
        void loadAssetPreview(MobileAssetStore.AssetInfo asset, ImageView target);
        void previewAsset(MobileAssetStore.AssetInfo asset);
        void editAssetTags(MobileAssetStore.AssetInfo asset);
        void confirmRemoveAsset(MobileAssetStore.AssetInfo asset);
        void refreshAssetLibrary(String query);
        String activeProjectName();
    }

    public interface PipelineHost {
        ViewGroup pageContainer();
        void showHomePage();
        List<ProjectRepository.ProjectInfo> listProjects();
        String pipelineStageFor(long projectId);
        String pipelineStage();
        String activeProjectStatus();
        String statusLabel(String status);
        void openProject(long projectId);
        boolean exportActive();
        void cancelActiveExport();
        void launchVideoPicker();
        void startProjectExport();
        void completeCurrentStage();
        boolean autoPipelineActive();
        void cancelAutoPipeline();
        void runAutomaticPipeline();
        List<TimelineClip> activeProjectClips();
        void autoPlanEffectsAndRender();
    }

    public interface HomeHost {
        ViewGroup pageContainer();
        String versionName();
        void showPipelinePage();
        void showShotSearchPage();
        void showPlatformImportPage();
        void showAssetLibraryPage();
        void showCompilationsPage();
        void showAiSettingsPage();
        void showGuidePage();
        void showReleaseNotesPage();
        void showPublicAssetSearch();
        void showSourceDirectory();
        void showSettingsPage();
        void createProjectAndPickVideo();
        void createProjectWithBrief(String name, String gameCategory, String commentaryStyle, String editingScope,
                                    int targetDurationSeconds, String brief, String terminologyGlossary,
                                    boolean storyboardReviewEnabled, boolean automaticGenerationEnabled);
        List<ProjectRepository.ProjectInfo> listProjects();
        String statusLabel(String status);
        void openProject(long id);
        void showProjectActions(ProjectRepository.ProjectInfo project);
    }

    public interface RevisionHost {
        List<ProjectRepository.RevisionInfo> listRevisions();
        List<TimelineClip> currentClips();
        List<TimelineClip> loadRevisionClips(long id);
        void restoreRevision(ProjectRepository.RevisionInfo revision);
        void showRevisionActions(ProjectRepository.RevisionInfo revision);
        void showRevisionComparePicker();
        void applyRevisionMerge(ProjectRepository.RevisionInfo revision, RevisionMerge.Mode mode);
        String formatDuration(long ms);
        void logEvent(String source, String message);
        void unavailable(String message);
    }

    public interface TrackHost {
        TimelineClip currentClip();
        void beginEdit(String description);
        void commitEdit(String reason);
        void renderTimeline();
        void setStatus(String text);
        void unavailable(String message);
        ProjectRepository.TrackState trackState(String type);
        void updateTrackState(String type, boolean muted, boolean solo);
        void refreshTrackControls();
    }

    public interface SubtitleHost {
        List<ProjectRepository.SubtitleCueInfo> listSubtitleCues();
        void replaceSubtitleCues(List<SubtitleFileCodec.Cue> cues);
        void clearSubtitleCues();
        void beginEdit(String description);
        void commitEdit(String reason);
        void renderTimeline();
        void setStatus(String text);
        void unavailable(String message);
        void showErrorDialog(String title, String message);
        String formatDuration(long ms);
        String activeProjectName();
        void launchSubtitleFileOpen();
        void launchSubtitleFileCreate(String safeName);
        void setPendingSubtitleFile(String content);
        void refreshSubtitleFiles();
    }

    public interface EditingHost {
        TimelineClip currentClip();
        void beginEdit(String description);
        void commitEdit(String reason);
        void renderTimeline();
        void setStatus(String text);
        void unavailable(String message);
        void showErrorDialog(String title, String message);
        ProjectRepository.ClipAudioConfig clipAudioConfig(String clipKey);
        void saveClipAudioConfig(String clipKey, float volume, long in, long out);
        ProjectRepository.ClipVisualConfig clipVisualConfig(String clipKey);
        void saveClipVisualConfig(String clipKey, float brightness, float contrast, float saturation,
                                  float temperature, float hue, float scale, float rotation);
        void seekTo(long positionMs);
        void exportSingleClip(TimelineClip clip);
        void deriveCoverAsset(TimelineClip clip);
        ExecutorService thumbnails();
        Handler handler();
        long valueOf(EditText field);
        float floatValueOf(EditText field);
    }

    public interface KeyframeHost {
        TimelineClip currentClip();
        long playbackPosition();
        String formatDuration(long ms);
        void beginEdit(String description);
        void commitEdit(String reason);
        void renderTimeline();
        void setStatus(String text);
        void unavailable(String message);
        void showErrorDialog(String title, String message);
        void saveKeyframe(String clipKey, String property, long timeMs, float value);
        void saveKeyframe(String clipKey, String property, long timeMs, float value, String easing);
        List<ProjectRepository.KeyframeInfo> listKeyframes(String clipKey, String property);
        void clearKeyframes(String clipKey, String property);
        void replaceKeyframes(String clipKey, String property, List<ProjectRepository.KeyframeInfo> frames);
        float floatValueOf(EditText field);
        long valueOf(EditText field);
    }

    public interface PlacementHost {
        TimelineClip currentClip();
        long currentClipDurationOrMax();
        List<MobileAssetStore.PlacementInfo> listPlacements();
        List<MobileAssetStore.AssetInfo> listAssets();
        void placeAsset(String clipKey, long assetId, String role);
        void updatePlacementMix(long id, float volume, long in, long out);
        void updatePlacementVisual(long id, float x, float y, float scale);
        void removePlacement(long id);
        void beginEdit(String description);
        void commitEdit(String reason);
        void renderTimeline();
        void setStatus(String text);
        void unavailable(String message);
        void showAssetLibraryPage();
        long valueOf(EditText field);
        float floatValueOf(EditText field);
    }

    public interface TemplateHost {
        TimelineClip currentClip();
        List<ProjectRepository.EffectTemplateInfo> listEffectTemplates();
        void deleteEffectTemplate(long id);
        void createEffectTemplate(String name, String cue);
        void beginEdit(String description);
        void commitEdit(String reason);
        void renderTimeline();
        void setStatus(String text);
        void unavailable(String message);
        void showErrorDialog(String title, String message);
        void logEvent(String source, String message);
        void refreshTemplates();
        void exportTemplates();
        void importTemplates();
    }

    public interface StoryboardHost {
        TimelineClip currentClip();
        List<TimelineClip> allClips();
        void beginEdit(String description);
        void commitEdit(String reason);
        void renderTimeline();
        void setStatus(String text);
        void unavailable(String message);
        ProjectRepository.ClipReviewInfo clipReview(String clipKey);
        void reviewClip(String clipKey, String status, String note);
        void selectClip(int index);
        void openStoryboardPanel();
    }

    public interface NarrationHost {
        TimelineClip currentClip();
        boolean ttsReady();
        List<String> availableOfflineVoiceNames();
        String voiceDisplayName(String voiceName);
        String configuredVoiceName(String clipKey);
        float configuredSpeed(String clipKey);
        float configuredPitch(String clipKey);
        void saveVoiceConfig(String clipKey, String voiceName, float speed, float pitch);
        void startNarration(TimelineClip clip, String voiceName, float speed, float pitch);
        void showErrorDialog(String title, String message);
        void unavailable(String message);
    }

    public interface EditorHost {
        ViewGroup pageContainer();
        void showHomePage();
        String activeProjectName();
        void openRevisionPanel();
        void showExportJobs();
        void showPipelinePanel();
        void startProjectExport();
        ExoPlayer player();
        void playOrPause();
        void stepFrame(int direction);
        void markBoundary(boolean inPoint);
        void showPlaybackSpeed();
        void launchVideoPicker();
        int timelineZoom();
        void setTimelineZoom(int value);
        void renderTimeline();
        boolean handleTimelineDrag(DragEvent event, ViewGroup timeline);
        void undo();
        void redo();
        void split();
        void mergeRight();
        void showTrackAssignment();
        void confirmDelete();
        void openClipPanel();
        void showClipVisualAdjustments();
        void showKeyframes();
        void showPositionKeyframes();
        void showOpacityKeyframes();
        void showVolumeKeyframes();
        void showKeyframeCurveEditor();
        void showEffectTemplates();
        void openSubtitlePanel();
        void reviewCurrentClip();
        void showScriptQuality();
        void showSubtitleFiles();
        void openAssetPlacement();
        void addCurrentClipToCompilation();
        void synthesizeNarration();
        void showTrackControls();
        int screenWidthDp();
        boolean landscape();
        void attachEditorViews(LinearLayout timeline, TextView ruler, TextView projectSummary,
                               TextView emptyTimeline, WaveformView waveformView, LinearLayout subtitleTrack,
                               LinearLayout narrationTrack, LinearLayout effectTrack, LinearLayout assetTrack,
                               TextView status);
        void selectCurrentIfValid();
    }

    private final Context context;
    private final ExportDialogController exportDialogs;
    private final DiagnosticsDialogController diagnosticsDialogs;
    private final StoryboardDialogController storyboardDialogs;
    private final TaskDialogController taskDialogs;

    public DialogController(Context context) {
        this(context, null, null, null, null, null, null);
    }

    public DialogController(Context context, ExportHost exportHost) {
        this(context, exportHost, null, null, null, null, null);
    }

    public DialogController(Context context, ExportHost exportHost, ProjectHost projectHost) {
        this(context, exportHost, projectHost, null, null, null, null);
    }

    public DialogController(Context context, ExportHost exportHost, ProjectHost projectHost,
                            CompilationHost compilationHost) {
        this(context, exportHost, projectHost, compilationHost, null, null, null);
    }

    public DialogController(Context context, ExportHost exportHost, ProjectHost projectHost,
                            CompilationHost compilationHost, PlatformImportHost platformImportHost) {
        this(context, exportHost, projectHost, compilationHost, platformImportHost, null, null);
    }

    public DialogController(Context context, ExportHost exportHost, ProjectHost projectHost,
                            CompilationHost compilationHost, PlatformImportHost platformImportHost,
                            SettingsHost settingsHost) {
        this(context, exportHost, projectHost, compilationHost, platformImportHost, settingsHost, null);
    }

    public DialogController(Context context, ExportHost exportHost, ProjectHost projectHost,
                            CompilationHost compilationHost, PlatformImportHost platformImportHost,
                            SettingsHost settingsHost, ShotSearchHost shotSearchHost) {
        this.context = context;
        this.exportDialogs = new ExportDialogController(context, exportHost, this);
        this.diagnosticsDialogs = new DiagnosticsDialogController(context, settingsHost, this);
        this.storyboardDialogs = new StoryboardDialogController(context, this);
        this.taskDialogs = new TaskDialogController(context, projectHost, compilationHost, platformImportHost, shotSearchHost, this);
    }

    public void showExportJobs() { exportDialogs.showExportJobs(); }
    public void showExportProgress(String dialogTitle, String label) { exportDialogs.showExportProgress(dialogTitle, label); }
    public void updateExportProgress(int percent) { exportDialogs.updateExportProgress(percent); }
    public void dismissExportProgress() { exportDialogs.dismissExportProgress(); }
    public void showCustomExport() { exportDialogs.showCustomExport(); }
    public void showExportActions(File file) { exportDialogs.showExportActions(file); }
    public Uri exportUri(File file) { return exportDialogs.exportUri(file); }
    public void openExportFile(File file) { exportDialogs.openExportFile(file); }
    public void shareExportFile(File file) { exportDialogs.shareExportFile(file); }
    public void showInstalledVoices(List<String> voiceLabels) { diagnosticsDialogs.showInstalledVoices(voiceLabels); }
    public void showGuide(String versionName) { diagnosticsDialogs.showGuide(versionName); }
    public void showReleaseNotes(String versionName) { diagnosticsDialogs.showReleaseNotes(versionName); }
    public void showAiSettings() { diagnosticsDialogs.showAiSettings(); }
    public void showSettings() { diagnosticsDialogs.showSettings(); }
    public void openSubtitlePanel() { storyboardDialogs.openSubtitlePanel(); }
    public void reviewCurrentClip() { storyboardDialogs.reviewCurrentClip(); }
    public void showScriptQuality() { storyboardDialogs.showScriptQuality(); }
    public void showEditor() { storyboardDialogs.showEditor(); }
    public void showProjectActions(ProjectRepository.ProjectInfo project) { taskDialogs.showProjectActions(project); }
    public void showRevisionActions(ProjectRepository.RevisionInfo revision) { taskDialogs.showRevisionActions(revision); }
    public void createCompilation(TimelineClip pending) { taskDialogs.createCompilation(pending); }
    public void showAddToCompilation(TimelineClip clip) { taskDialogs.showAddToCompilation(clip); }
    public void showCompilations() { taskDialogs.showCompilations(); }
    public void openCompilation(ProjectRepository.CompilationInfo compilation) { taskDialogs.openCompilation(compilation); }
    public void showPlatformImport() { taskDialogs.showPlatformImport(); }
    public void showShotSearch(String query) { taskDialogs.showShotSearch(query); }
    public void showAssetLibrary(String query) { taskDialogs.showAssetLibrary(query); }
    public void showPublicAssetSearch() { taskDialogs.showPublicAssetSearch(); }
    public void showSourceDirectory() { taskDialogs.showSourceDirectory(); }
    public void showPipeline() { taskDialogs.showPipeline(); }
    public void showPipelinePanel() { taskDialogs.showPipelinePanel(); }
    public void showHome() { taskDialogs.showHome(); }
    public void openRevisionPanel() { taskDialogs.openRevisionPanel(); }
    public void showRevisionComparePicker() { taskDialogs.showRevisionComparePicker(); }
    public void showRevisionDiff(ProjectRepository.RevisionInfo revision) { taskDialogs.showRevisionDiff(revision); }
    public void showRevisionMergeOptions(ProjectRepository.RevisionInfo revision) { taskDialogs.showRevisionMergeOptions(revision); }
    public void showTrackAssignment() { taskDialogs.showTrackAssignment(); }
    public void showTrackControls() { taskDialogs.showTrackControls(); }
    public void showSubtitleFiles() { taskDialogs.showSubtitleFiles(); }
    public void editSubtitleCue(int index) { taskDialogs.editSubtitleCue(index); }
    public void openClipPanel() { taskDialogs.openClipPanel(); }
    public void showClipVisualAdjustments() { taskDialogs.showClipVisualAdjustments(); }
    public void showKeyframes() { taskDialogs.showKeyframes(); }
    public void showPositionKeyframes() { taskDialogs.showPositionKeyframes(); }
    public void showOpacityKeyframes() { taskDialogs.showOpacityKeyframes(); }
    public void showVolumeKeyframes() { taskDialogs.showVolumeKeyframes(); }
    public void showKeyframeCurveEditor() { taskDialogs.showKeyframeCurveEditor(); }
    public void addKeyframeDialog(TimelineClip clip, String property) { taskDialogs.addKeyframeDialog(clip, property); }
    public void openAssetPlacement() { taskDialogs.openAssetPlacement(); }
    public void configurePlacement(MobileAssetStore.PlacementInfo placement) { taskDialogs.configurePlacement(placement); }
    public void showEffectTemplates() { taskDialogs.showEffectTemplates(); }
    public void applyEffectTemplate(ProjectRepository.EffectTemplateInfo template) { taskDialogs.applyEffectTemplate(template); }
    public void saveCurrentEffectTemplate() { taskDialogs.saveCurrentEffectTemplate(); }
    public void synthesizeNarration() { taskDialogs.synthesizeNarration(); }

    public int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    public GradientDrawable outlined(int color, int radius) {
        GradientDrawable drawable = shape(color, radius);
        drawable.setStroke(dp(3), TEXT);
        return drawable;
    }

    public LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    public LinearLayout row() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    public TextView label(String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    public TextView sectionTitle(String value) {
        return label(value, 17, TEXT, true);
    }

    public Button action(String text, int background, int foreground, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        boolean compact = ScreenAdapter.compact(context.getResources().getConfiguration().screenWidthDp);
        button.setMinHeight(dp(ScreenAdapter.touchTarget(0, compact)));
        button.setBackground(outlined(background, 0));
        button.setElevation(dp(5));
        button.setOnClickListener(listener);
        return button;
    }

    public Button navButton(String text, int color, View.OnClickListener listener) {
        Button button = action(text, color, TEXT, listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(104), dp(48));
        params.setMargins(0, 0, dp(9), 0);
        button.setLayoutParams(params);
        return button;
    }

    public Button iconButton(String text, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setBackgroundResource(R.drawable.ic_back);
        button.setContentDescription("返回");
        button.setOnClickListener(listener);
        return button;
    }

    public LinearLayout tool(String icon, String title, View.OnClickListener listener) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setOnClickListener(listener);
        TextView glyph = label(icon, 20, TEXT, true);
        glyph.setGravity(Gravity.CENTER);
        item.addView(glyph);
        TextView caption = label(title, 11, MUTED, false);
        caption.setGravity(Gravity.CENTER);
        item.addView(caption);
        item.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(68)));
        return item;
    }

    public EditText number(String hint, long value) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setText(String.valueOf(value));
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setBackground(shape(SURFACE_HIGH, 12));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams params = match(dp(58));
        params.setMargins(0, dp(8), 0, dp(4));
        input.setLayoutParams(params);
        return input;
    }

    public EditText creativeInput(String hint, String value) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setBackground(outlined(Color.WHITE, 0));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    public LinearLayout.LayoutParams match(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    public LinearLayout card() {
        return card(SURFACE);
    }

    public LinearLayout card(int color) {
        LinearLayout card = column();
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(outlined(color, 0));
        card.setElevation(dp(8));
        return card;
    }

    public LinearLayout sheet(String title) {
        LinearLayout panel = column();
        panel.setPadding(dp(22), dp(18), dp(22), dp(30));
        panel.setBackgroundColor(SURFACE);
        panel.addView(label(title, 22, TEXT, true));
        return panel;
    }

    public void showError(String title, String message) {
        new AlertDialog.Builder(context).setTitle(title)
                .setMessage(message == null ? "未知错误" : message)
                .setPositiveButton("知道了", null).show();
    }









    public void attachSettingsPageHost(SettingsPageHost host) {
        diagnosticsDialogs.attachSettingsPageHost(host);
    }

    public void attachAssetLibraryHost(AssetLibraryHost host) { taskDialogs.attachAssetLibraryHost(host); }

    public void attachPipelineHost(PipelineHost host) { taskDialogs.attachPipelineHost(host); }

    public void attachHomeHost(HomeHost host) { taskDialogs.attachHomeHost(host); }

    public void attachRevisionHost(RevisionHost host) { taskDialogs.attachRevisionHost(host); }

    public void attachTrackHost(TrackHost host) { taskDialogs.attachTrackHost(host); }

    public void attachSubtitleHost(SubtitleHost host) { taskDialogs.attachSubtitleHost(host); }

    public void attachEditingHost(EditingHost host) { taskDialogs.attachEditingHost(host); }

    public void attachKeyframeHost(KeyframeHost host) { taskDialogs.attachKeyframeHost(host); }

    public void attachPlacementHost(PlacementHost host) { taskDialogs.attachPlacementHost(host); }

    public void attachTemplateHost(TemplateHost host) { taskDialogs.attachTemplateHost(host); }

    public void attachStoryboardHost(StoryboardHost host) {
        storyboardDialogs.attachStoryboardHost(host);
    }

    public void attachNarrationHost(NarrationHost host) { taskDialogs.attachNarrationHost(host); }

    public void attachEditorHost(EditorHost host) {
        storyboardDialogs.attachEditorHost(host);
    }





























    public static long longValue(EditText field) {
        try {
            return Long.parseLong(field.getText().toString());
        } catch (Exception ignored) {
            return 0;
        }
    }

    public void confirm(String title, String message, String confirmLabel, Runnable onConfirm) {
        new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton(confirmLabel, (dialog, which) -> {
                    if (onConfirm != null) onConfirm.run();
                }).show();
    }

}
