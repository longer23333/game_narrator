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

@UnstableApi
public final class TaskDialogController {
    private final ProjectTaskDialogController projectDialogs;
    private final TimelineTaskDialogController timelineDialogs;

    public TaskDialogController(Context context, DialogController.ProjectHost projectHost,
                                DialogController.CompilationHost compilationHost,
                                DialogController.PlatformImportHost platformImportHost,
                                DialogController.ShotSearchHost shotSearchHost,
                                DialogController ui) {
        this.projectDialogs = new ProjectTaskDialogController(context, projectHost, compilationHost,
                platformImportHost, shotSearchHost, ui);
        this.timelineDialogs = new TimelineTaskDialogController(context, ui);
    }

    public void attachAssetLibraryHost(DialogController.AssetLibraryHost host) { projectDialogs.attachAssetLibraryHost(host); }
    public void attachPipelineHost(DialogController.PipelineHost host) { projectDialogs.attachPipelineHost(host); }
    public void attachHomeHost(DialogController.HomeHost host) { projectDialogs.attachHomeHost(host); }
    public void attachRevisionHost(DialogController.RevisionHost host) { projectDialogs.attachRevisionHost(host); }
    public void attachTrackHost(DialogController.TrackHost host) { timelineDialogs.attachTrackHost(host); }
    public void attachSubtitleHost(DialogController.SubtitleHost host) { timelineDialogs.attachSubtitleHost(host); }
    public void attachEditingHost(DialogController.EditingHost host) { timelineDialogs.attachEditingHost(host); }
    public void attachKeyframeHost(DialogController.KeyframeHost host) { timelineDialogs.attachKeyframeHost(host); }
    public void attachPlacementHost(DialogController.PlacementHost host) { timelineDialogs.attachPlacementHost(host); }
    public void attachTemplateHost(DialogController.TemplateHost host) { timelineDialogs.attachTemplateHost(host); }
    public void attachNarrationHost(DialogController.NarrationHost host) { timelineDialogs.attachNarrationHost(host); }

    public void showProjectActions(ProjectRepository.ProjectInfo project) { projectDialogs.showProjectActions(project); }
    public void showRevisionActions(ProjectRepository.RevisionInfo revision) { projectDialogs.showRevisionActions(revision); }
    public void createCompilation(TimelineClip pending) { projectDialogs.createCompilation(pending); }
    public void showAddToCompilation(TimelineClip clip) { projectDialogs.showAddToCompilation(clip); }
    public void showCompilations() { projectDialogs.showCompilations(); }
    public void openCompilation(ProjectRepository.CompilationInfo compilation) { projectDialogs.openCompilation(compilation); }
    public void showPlatformImport() { projectDialogs.showPlatformImport(); }
    public void showShotSearch(String query) { projectDialogs.showShotSearch(query); }
    public void showAssetLibrary(String query) { projectDialogs.showAssetLibrary(query); }
    public void showPublicAssetSearch() { projectDialogs.showPublicAssetSearch(); }
    public void showSourceDirectory() { projectDialogs.showSourceDirectory(); }
    public void showPipeline() { projectDialogs.showPipeline(); }
    public void showPipelinePanel() { projectDialogs.showPipelinePanel(); }
    public void showHome() { projectDialogs.showHome(); }
    public void openRevisionPanel() { projectDialogs.openRevisionPanel(); }
    public void showRevisionComparePicker() { projectDialogs.showRevisionComparePicker(); }
    public void showRevisionDiff(ProjectRepository.RevisionInfo revision) { projectDialogs.showRevisionDiff(revision); }
    public void showRevisionMergeOptions(ProjectRepository.RevisionInfo revision) { projectDialogs.showRevisionMergeOptions(revision); }
    public void showTrackAssignment() { timelineDialogs.showTrackAssignment(); }
    public void showTrackControls() { timelineDialogs.showTrackControls(); }
    public void showSubtitleFiles() { timelineDialogs.showSubtitleFiles(); }
    public void editSubtitleCue(int index) { timelineDialogs.editSubtitleCue(index); }
    public void openClipPanel() { timelineDialogs.openClipPanel(); }
    public void showClipVisualAdjustments() { timelineDialogs.showClipVisualAdjustments(); }
    public void showKeyframes() { timelineDialogs.showKeyframes(); }
    public void showPositionKeyframes() { timelineDialogs.showPositionKeyframes(); }
    public void showOpacityKeyframes() { timelineDialogs.showOpacityKeyframes(); }
    public void showVolumeKeyframes() { timelineDialogs.showVolumeKeyframes(); }
    public void showKeyframeCurveEditor() { timelineDialogs.showKeyframeCurveEditor(); }
    public void addKeyframeDialog(TimelineClip clip, String property) { timelineDialogs.addKeyframeDialog(clip, property); }
    public void openAssetPlacement() { timelineDialogs.openAssetPlacement(); }
    public void configurePlacement(MobileAssetStore.PlacementInfo placement) { timelineDialogs.configurePlacement(placement); }
    public void showEffectTemplates() { timelineDialogs.showEffectTemplates(); }
    public void applyEffectTemplate(ProjectRepository.EffectTemplateInfo template) { timelineDialogs.applyEffectTemplate(template); }
    public void saveCurrentEffectTemplate() { timelineDialogs.saveCurrentEffectTemplate(); }
    public void synthesizeNarration() { timelineDialogs.synthesizeNarration(); }
}
