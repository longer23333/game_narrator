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
public final class MainActivityActions {
    private final MainActivity activity;
    private final MainActivityPageActions pageActions;
    private final MainActivityEditActions editActions;
    private final MainActivityExportActions exportActions;

    public MainActivityActions(MainActivity activity) {
        this.activity = activity;
        this.pageActions = new MainActivityPageActions(activity);
        this.editActions = new MainActivityEditActions(activity);
        this.exportActions = new MainActivityExportActions(activity);
    }

    void unavailable(String message) { if (activity.status != null) activity.status.setText(message); Toast.makeText(activity, message, Toast.LENGTH_SHORT).show(); }

    void showError(String title, String message) { activity.dialogs.showError(title, message); }

    LinearLayout sheet(String title) { return activity.dialogs.sheet(title); }

    LinearLayout card() { return activity.dialogs.card(); }

    LinearLayout card(int color) { return activity.dialogs.card(color); }

    LinearLayout column() { return activity.dialogs.column(); }

    LinearLayout row() { return activity.dialogs.row(); }

    TextView sectionTitle(String value) { return activity.dialogs.sectionTitle(value); }

    TextView label(String value, float size, int color, boolean bold) { return activity.dialogs.label(value, size, color, bold); }

    Button action(String text, int background, int foreground, View.OnClickListener listener) { return activity.dialogs.action(text, background, foreground, listener); }

    Button navButton(String text, int color, View.OnClickListener listener) { return activity.dialogs.navButton(text, color, listener); }

    Button iconButton(String text, View.OnClickListener listener) { return activity.dialogs.iconButton(text, listener); }

    LinearLayout tool(String icon, String title, View.OnClickListener listener) { return activity.dialogs.tool(icon, title, listener); }

    EditText number(String hint, long value) { return activity.dialogs.number(hint, value); }

    EditText creativeInput(String hint,String value){ return activity.dialogs.creativeInput(hint,value); }

    GradientDrawable shape(int color, int radius) { return activity.dialogs.shape(color, radius); }

    GradientDrawable outlined(int color, int radius) { return activity.dialogs.outlined(color, radius); }

    LinearLayout.LayoutParams match(int height) { return activity.dialogs.match(height); }

    int dp(int value) { return activity.dialogs.dp(value); }

    void showHome() { pageActions.showHome(); }
    void showAssetLibrary() { pageActions.showAssetLibrary(); }
    void showAiSettings() { pageActions.showAiSettings(); }
    String buildEngineReport() { return pageActions.buildEngineReport(); }
    void showInstalledVoices() { pageActions.showInstalledVoices(); }
    void showGuide() { pageActions.showGuide(); }
    void maybeShowFirstRunGuide() { pageActions.maybeShowFirstRunGuide(); }
    void showReleaseNotes() { pageActions.showReleaseNotes(); }
    void showShotSearch() { pageActions.showShotSearch(); }
    void showShotSearch(String query) { pageActions.showShotSearch(query); }
    void openShotFromSearch(TimelineClip clip) { pageActions.openShotFromSearch(clip); }
    void showRevisionComparePicker() { pageActions.showRevisionComparePicker(); }
    void showRevisionDiff(ProjectRepository.RevisionInfo revision) { pageActions.showRevisionDiff(revision); }
    void showRevisionMergeOptions(ProjectRepository.RevisionInfo revision) { pageActions.showRevisionMergeOptions(revision); }
    void applyRevisionMerge(ProjectRepository.RevisionInfo revision,RevisionMerge.Mode mode) { pageActions.applyRevisionMerge(revision, mode); }
    void showKeyframeCurveEditor() { pageActions.showKeyframeCurveEditor(); }
    void addKeyframeDialog(TimelineClip clip,String property) { pageActions.addKeyframeDialog(clip, property); }
    void previewAsset(MobileAssetStore.AssetInfo asset) { pageActions.previewAsset(asset); }
    void deriveCoverAsset(TimelineClip clip) { pageActions.deriveCoverAsset(clip); }
    void showEffectTemplates() { pageActions.showEffectTemplates(); }
    void applyEffectTemplate(ProjectRepository.EffectTemplateInfo template) { pageActions.applyEffectTemplate(template); }
    void beginTemplateExport() { pageActions.beginTemplateExport(); }
    void importTemplates() { pageActions.importTemplates(); }
    void writeTemplateExport(Uri uri) { pageActions.writeTemplateExport(uri); }
    void restoreTemplateExport(Uri uri) { pageActions.restoreTemplateExport(uri); }
    void saveCurrentEffectTemplate() { pageActions.saveCurrentEffectTemplate(); }
    void showPipeline() { pageActions.showPipeline(); }
    void showPipelinePanel() { pageActions.showPipelinePanel(); }
    void completeCurrentStage() { pageActions.completeCurrentStage(); }
    void autoPlanEffectsAndRender() { pageActions.autoPlanEffectsAndRender(); }
    void runAutomaticPipeline() { pageActions.runAutomaticPipeline(); }
    void autoNarrateNext(List<TimelineClip> pending,int index) { pageActions.autoNarrateNext(pending, index); }
    void showTrackAssignment() { pageActions.showTrackAssignment(); }
    File runtimeLogFile() { return pageActions.runtimeLogFile(); }
    void logEvent(String source,String message) { pageActions.logEvent(source, message); }
    String readLogTail(File file,int lines) { return pageActions.readLogTail(file, lines); }
    void exportStructuredLog() { pageActions.exportStructuredLog(); }
    void confirmClearStructuredLog() { pageActions.confirmClearStructuredLog(); }
    void showPlatformImport() { pageActions.showPlatformImport(); }
    void startRemoteImport(String url,String format,boolean createProject) { pageActions.startRemoteImport(url, format, createProject); }
    void startRemoteImport(String url,boolean createProject) { pageActions.startRemoteImport(url, createProject); }
    void showSettings() { pageActions.showSettings(); }
    void shareDiagnostics(String report) { pageActions.shareDiagnostics(report); }
    void confirmPermanentDelete(ProjectRepository.ProjectInfo project) { pageActions.confirmPermanentDelete(project); }
    void beginProjectArchive() { pageActions.beginProjectArchive(); }
    void writeProjectArchive(Uri uri) { pageActions.writeProjectArchive(uri); }
    void restoreProjectArchive(Uri uri) { pageActions.restoreProjectArchive(uri); }
    static byte[] readLimited(java.io.InputStream input,int limit)throws java.io.IOException { return MainActivityPageActions.readLimited(input, limit); }
    void showAssetLibrary(String query) { pageActions.showAssetLibrary(query); }
    void addAssets(List<Uri> uris) { pageActions.addAssets(uris); }
    void loadAssetPreview(MobileAssetStore.AssetInfo asset,ImageView target) { pageActions.loadAssetPreview(asset, target); }
    void editAssetTags(MobileAssetStore.AssetInfo asset) { pageActions.editAssetTags(asset); }
    void confirmRemoveAsset(MobileAssetStore.AssetInfo asset) { pageActions.confirmRemoveAsset(asset); }
    void showEditor() { pageActions.showEditor(); }
    void openProject(long projectId) { pageActions.openProject(projectId); }
    void showProjectActions(ProjectRepository.ProjectInfo project) { pageActions.showProjectActions(project); }
    void showCompilations() { pageActions.showCompilations(); }
    void createCompilationDialog(TimelineClip pending) { pageActions.createCompilationDialog(pending); }
    void addCurrentClipToCompilation() { pageActions.addCurrentClipToCompilation(); }
    void openCompilation(ProjectRepository.CompilationInfo compilation) { pageActions.openCompilation(compilation); }
    void materializeCompilation(ProjectRepository.CompilationInfo compilation,List<ProjectRepository.CompilationItemInfo> items) { pageActions.materializeCompilation(compilation, items); }
    static String statusLabel(String status) { return MainActivityPageActions.statusLabel(status); }
    void addVideos(List<Uri> uris) { editActions.addVideos(uris); }
    void renderTimeline() { editActions.renderTimeline(); }
    void selectClip(int index) { editActions.selectClip(index); }
    void stepFrame(int direction) { editActions.stepFrame(direction); }
    void markBoundary(boolean inPoint) { editActions.markBoundary(inPoint); }
    void showPlaybackSpeed() { editActions.showPlaybackSpeed(); }
    void openRevisionPanel() { editActions.openRevisionPanel(); }
    void showRevisionActions(ProjectRepository.RevisionInfo revision) { editActions.showRevisionActions(revision); }
    void restoreRevision(ProjectRepository.RevisionInfo revision) { editActions.restoreRevision(revision); }
    void openClipPanel() { editActions.openClipPanel(); }
    void showKeyframes() { editActions.showKeyframes(); }
    void showPositionKeyframes() { editActions.showPositionKeyframes(); }
    void showOpacityKeyframes() { editActions.showOpacityKeyframes(); }
    void showVolumeKeyframes() { editActions.showVolumeKeyframes(); }
    void showSubtitleFiles() { editActions.showSubtitleFiles(); }
    void editSubtitleCue(int index) { editActions.editSubtitleCue(index); }
    void importSubtitleFile(Uri uri) { editActions.importSubtitleFile(uri); }
    void writeSubtitleFile(Uri uri) { editActions.writeSubtitleFile(uri); }
    void showClipVisualAdjustments() { editActions.showClipVisualAdjustments(); }
    void openSubtitlePanel() { editActions.openSubtitlePanel(); }
    void reviewCurrentClip() { editActions.reviewCurrentClip(); }
    void showScriptQuality() { editActions.showScriptQuality(); }
    void synthesizeNarration() { editActions.synthesizeNarration(); }
    void generateNarration(TimelineClip clip,android.speech.tts.Voice voice,float speed,float pitch) { editActions.generateNarration(clip, voice, speed, pitch); }
    void showTrackControls() { editActions.showTrackControls(); }
    void openAssetPlacement() { editActions.openAssetPlacement(); }
    void configurePlacement(MobileAssetStore.PlacementInfo placement) { editActions.configurePlacement(placement); }
    void split() { editActions.split(); }
    void move(int delta) { editActions.move(delta); }
    void confirmDelete() { editActions.confirmDelete(); }
    void removeSelected() { editActions.removeSelected(); }
    void mergeRight() { editActions.mergeRight(); }
    void pushHistory() { editActions.pushHistory(); }
    void pushHistory(String description) { editActions.pushHistory(description); }
    void undo() { editActions.undo(); }
    void redo() { editActions.redo(); }
    void afterHistoryChange() { editActions.afterHistoryChange(); }
    static String joinCreativeText(String left,String right) { return MainActivityEditActions.joinCreativeText(left, right); }
    void persistProject(String reason) { editActions.persistProject(reason); }
    TimelineClip current() { return editActions.current(); }
    long totalDuration() { return editActions.totalDuration(); }
    static String format(long ms) { return MainActivityEditActions.format(ms); }
    static String buildRuler(long total) { return MainActivityEditActions.buildRuler(total); }
    long value(EditText field) { return editActions.value(field); }
    float floatValue(EditText field) { return editActions.floatValue(field); }
    void exportSingleClip(TimelineClip clip) { exportActions.exportSingleClip(clip); }
    void startSingleClipExport(TimelineClip clip, ExportController.Preset preset) { exportActions.startSingleClipExport(clip, preset); }
    void showExportActions(File file) { exportActions.showExportActions(file); }
    Uri exportUri(File file) { return exportActions.exportUri(file); }
    void openExportFile(File file) { exportActions.openExportFile(file); }
    void shareExportFile(File file) { exportActions.shareExportFile(file); }
    void cancelActiveExport() { exportActions.cancelActiveExport(); }
    void exportProject() { exportActions.exportProject(); }
    static boolean hasEncoder(String mime) { return MainActivityExportActions.hasEncoder(mime); }
    void startExport(ExportController.Preset preset) { exportActions.startExport(preset); }
    long readDuration(Uri uri) { return exportActions.readDuration(uri); }
    String displayName(Uri uri) { return exportActions.displayName(uri); }
}
