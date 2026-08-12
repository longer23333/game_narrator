package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@UnstableApi
public final class MainActivity extends AppCompatActivity {
    static final ExportController.Preset[] EXPORT_PRESETS={
            new ExportController.Preset("快速 · 720p / 30 FPS / H.264",4_000_000,0,720,30,MimeTypes.VIDEO_H264),
            new ExportController.Preset("通用高清 · 1080p / 30 FPS / H.264",8_000_000,1920,1080,30,MimeTypes.VIDEO_H264),
            new ExportController.Preset("保持源画质 · 原分辨率 / 原帧率 / H.264",16_000_000,0,0,0,MimeTypes.VIDEO_H264)};
    static final int CANVAS = Color.rgb(255, 248, 232);
    static final int SURFACE = Color.WHITE;
    static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    static final int BRAND = Color.rgb(255, 229, 72);
    static final int ACCENT = Color.rgb(255, 79, 163);
    static final int BLUE = Color.rgb(54, 201, 255);
    static final int GREEN = Color.rgb(113, 230, 108);
    static final int TEXT = Color.rgb(17, 17, 17);
    static final int MUTED = Color.rgb(70, 80, 92);
    static final int DANGER = Color.rgb(255, 85, 119);

    final List<TimelineClip> clips = new ArrayList<>();
    final ExecutorService thumbnails = Executors.newSingleThreadExecutor();
    final ExecutorService downloads = Executors.newSingleThreadExecutor();
    final Handler handler = new Handler(Looper.getMainLooper());
    ExoPlayer player;
    PlaybackController playback;
    TimelineController timelineController;
    ExportController exportController;
    MobileProjectStore projectStore;
    ProjectState projectState;
    FrameLayout shell;
    LinearLayout timeline;
    TextView ruler;
    TextView projectSummary;
    TextView emptyTimeline;
    WaveformView waveformView;
    LinearLayout subtitleTrack;
    LinearLayout narrationTrack;
    LinearLayout effectTrack;
    LinearLayout assetTrack;
    TextView status;
    TimelineViewController timelineViewController;
    int selected = -1;
    boolean editorVisible;
    ActivityResultLauncher<String[]> picker;
    ActivityResultLauncher<String[]> assetPicker;
    ActivityResultLauncher<String> projectArchiveCreate;
    ActivityResultLauncher<String[]> projectArchiveOpen;
    ActivityResultLauncher<String> templateExportCreate;
    ActivityResultLauncher<String[]> templateExportOpen;
    ActivityResultLauncher<String> micPermission;
    ActivityResultLauncher<String[]> cookieFileOpen;
    ActivityResultLauncher<String[]> whisperWavOpen;
    ActivityResultLauncher<String[]> visionImageOpen;
    ActivityResultLauncher<String[]> subtitleFileOpen;
    ActivityResultLauncher<String> subtitleFileCreate;
    CookieFileSession cookieSession = new CookieFileSession();
    String pendingProjectArchive;
    String pendingTemplateExport;
    Runnable pendingMicAction;
    DialogController.WhisperResult pendingWhisperCallback;
    DialogController.ImageResult pendingVisionCallback;
    String pendingSubtitleFile;
    TextToSpeech textToSpeech;
    boolean ttsReady;
    Future<?> activeDownload;
    boolean autoPipelineActive;
    ProjectController projects;
    ProjectBackupManager projectBackups;
    PersistentSnapshotHistory projectHistory;
    DialogController dialogs;
    MainActivityActions actions;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(CANVAS);
        getWindow().setNavigationBarColor(CANVAS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        picker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::addVideos);
        assetPicker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::addAssets);
        projectArchiveCreate=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),this::writeProjectArchive);
        projectArchiveOpen=registerForActivityResult(new ActivityResultContracts.OpenDocument(),this::restoreProjectArchive);
        templateExportCreate=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),this::writeTemplateExport);
        templateExportOpen=registerForActivityResult(new ActivityResultContracts.OpenDocument(),this::restoreTemplateExport);
        micPermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            Runnable action = pendingMicAction;
            pendingMicAction = null;
            if (granted && action != null) action.run();
            else if (action != null) showError("需要麦克风权限", "请允许录音权限后重试。");
        });
        cookieFileOpen=registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleCookieFile);
        whisperWavOpen=registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleWhisperWav);
        visionImageOpen=registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleVisionImage);
        subtitleFileOpen=registerForActivityResult(new ActivityResultContracts.OpenDocument(),this::importSubtitleFile);
        subtitleFileCreate=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/x-subrip"),this::writeSubtitleFile);
        player = new ExoPlayer.Builder(this).build();
        projectStore = new MobileProjectStore(this);
        projectBackups = new ProjectBackupManager(this, projectStore.projects());
        thumbnails.execute(() -> {
            try {
                MobileModelBundler.ensureBundled(this);
            } catch (Exception ignored) { }
        });
        projectHistory = new PersistentSnapshotHistory(new SqliteEditHistoryStore(projectStore.database()));
        projectState = new ProjectState() {
            @Override public ProjectSnapshot capture() {
                return ProjectSnapshot.capture(projectStore, clips);
            }
            @Override public void restore(ProjectSnapshot snapshot) {
                snapshot.restore(projectStore, clips);
            }
        };
        timelineViewController = new TimelineViewController(this, clips, projectStore, new TimelineViewController.Host() {
            @Override public int selected() { return selected; }
            @Override public void selectClip(int index) { MainActivity.this.selectClip(index); }
            @Override public void renderTimeline() { MainActivity.this.renderTimeline(); }
            @Override public void pushHistory(String description) { MainActivity.this.pushHistory(description); }
            @Override public void persistProject(String reason) { MainActivity.this.persistProject(reason); }
            @Override public void setStatus(String text) { if (status != null) status.setText(text); }
            @Override public void unavailable(String message) { MainActivity.this.unavailable(message); }
            @Override public boolean editorVisible() { return editorVisible; }
            @Override public long currentPosition() { return player.getCurrentPosition(); }
            @Override public ExoPlayer player() { return player; }
            @Override public int dp(int value) { return MainActivity.this.dp(value); }
        }, thumbnails, handler);
        playback = new PlaybackController(this, player, message -> { if (status != null) status.setText(message); });
        timelineController = new TimelineController(clips, projectState, new TimelineController.Listener() {
            @Override public void onTimelineChanged() { renderTimeline(); }
            @Override public void onStatus(String message) { unavailable(message); }
        });
        exportController = new ExportController(this, clips, projectStore, handler, new ExportController.Listener() {
            @Override public void onExportStarted(String dialogTitle, String label, long jobId, File output) {
                dialogs.showExportProgress(dialogTitle, label);
            }
            @Override public void onProgress(int percent) {
                dialogs.updateExportProgress(percent);
            }
            @Override public void onCompleted(File output) {
                dialogs.dismissExportProgress();
                status.setText("导出完成：" + output.getAbsolutePath());
                showExportActions(output);
            }
            @Override public void onError(String message) {
                dialogs.dismissExportProgress();
                showError("导出失败", message);
            }
            @Override public void onCancelled() {
                dialogs.dismissExportProgress();
            }
            @Override public void onStatus(String message) {
                if (status != null) status.setText(message);
            }
            @Override public void showError(String title, String message) {
                MainActivity.this.showError(title, message);
            }
            @Override public void unavailable(String message) {
                MainActivity.this.unavailable(message);
            }
            @Override public void log(String source, String message) {
                logEvent(source, message);
            }
        });
        projects = new ProjectController(projectStore, clips, timelineController);
        MainActivityHosts hosts = new MainActivityHosts(this);
        dialogs = new DialogController(this, hosts.exportHost, hosts.projectHost,
                hosts.compilationHost, hosts.platformImportHost, hosts.settingsHost, hosts.shotSearchHost);
        hosts.attachTo(dialogs);
        actions = new MainActivityActions(this);
        textToSpeech=new TextToSpeech(this,statusCode->{if(statusCode==TextToSpeech.SUCCESS){int result=textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);ttsReady=result!=TextToSpeech.LANG_MISSING_DATA&&result!=TextToSpeech.LANG_NOT_SUPPORTED;}});
        clips.addAll(projectStore.loadClips());
        File moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        int recovered = projects.recoverInterruptedExports(moviesDir);
        if (recovered > 0) logEvent("export", "清理中断导出残留 " + recovered + " 个");
        shell = new FrameLayout(this);
        shell.setBackgroundColor(CANVAS);
        setContentView(shell);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (editorVisible) showHome(); else finish();
            }
        });
        showHome();
        logEvent("app", "应用启动 v" + BuildConfig.VERSION_NAME);
        handler.post(this::maybeShowFirstRunGuide);
    }
    void showHome() { actions.showHome(); }
    void showAssetLibrary() { actions.showAssetLibrary(); }
    void showAiSettings() { actions.showAiSettings(); }
    String buildEngineReport() { return actions.buildEngineReport(); }
    void showInstalledVoices() { actions.showInstalledVoices(); }
    void showGuide() { actions.showGuide(); }
    void maybeShowFirstRunGuide() { actions.maybeShowFirstRunGuide(); }
    void showReleaseNotes() { actions.showReleaseNotes(); }
    void showShotSearch() { actions.showShotSearch(); }
    void showShotSearch(String query) { actions.showShotSearch(query); }
    void openShotFromSearch(TimelineClip clip) { actions.openShotFromSearch(clip); }
    void exportSingleClip(TimelineClip clip) { actions.exportSingleClip(clip); }
    void startSingleClipExport(TimelineClip clip, ExportController.Preset preset) { actions.startSingleClipExport(clip, preset); }
    void showRevisionComparePicker() { actions.showRevisionComparePicker(); }
    void showRevisionDiff(ProjectRepository.RevisionInfo revision) { actions.showRevisionDiff(revision); }
    void showRevisionMergeOptions(ProjectRepository.RevisionInfo revision) { actions.showRevisionMergeOptions(revision); }
    void applyRevisionMerge(ProjectRepository.RevisionInfo revision,RevisionMerge.Mode mode) { actions.applyRevisionMerge(revision, mode); }
    void showKeyframeCurveEditor() { actions.showKeyframeCurveEditor(); }
    void addKeyframeDialog(TimelineClip clip,String property) { actions.addKeyframeDialog(clip, property); }
    void previewAsset(MobileAssetStore.AssetInfo asset) { actions.previewAsset(asset); }
    void deriveCoverAsset(TimelineClip clip) { actions.deriveCoverAsset(clip); }
    void showEffectTemplates() { actions.showEffectTemplates(); }
    void applyEffectTemplate(ProjectRepository.EffectTemplateInfo template) { actions.applyEffectTemplate(template); }
    void beginTemplateExport() { actions.beginTemplateExport(); }
    void importTemplates() { actions.importTemplates(); }
    void writeTemplateExport(Uri uri) { actions.writeTemplateExport(uri); }
    void restoreTemplateExport(Uri uri) { actions.restoreTemplateExport(uri); }

    void requestMicPermission(Runnable action) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        pendingMicAction = action;
        micPermission.launch(Manifest.permission.RECORD_AUDIO);
    }

    void launchCookieFileOpen() {
        cookieFileOpen.launch(new String[]{"text/plain", "text/*", "application/octet-stream"});
    }

    void handleCookieFile(Uri uri) {
        if (uri == null) return;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("无法读取 Cookie 文件");
            cookieSession.load(input);
            Toast.makeText(this, "已加载 " + cookieSession.domainCount() + " 个域名 Cookie（内存，1 小时后失效）",
                    Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            showError("Cookie 导入失败", error.getMessage());
        }
    }

    String cookieSessionFor(String url) {
        return cookieSession.cookiesForUrl(url);
    }

    void launchWhisperWavOpen(DialogController.WhisperResult callback) {
        pendingWhisperCallback = callback;
        whisperWavOpen.launch(new String[]{"audio/wav", "audio/x-wav", "audio/*"});
    }

    void handleWhisperWav(Uri uri) {
        DialogController.WhisperResult callback = pendingWhisperCallback;
        pendingWhisperCallback = null;
        if (uri == null) {
            if (callback != null) callback.onResult("未选择音频。");
            return;
        }
        File wav = new File(getCacheDir(), "whisper-input.wav");
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(wav)) {
            if (input == null) throw new IllegalStateException("无法读取音频");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        } catch (Exception error) {
            if (callback != null) callback.onResult("读取音频失败："
                    + (error.getMessage() == null ? "未知错误" : error.getMessage()));
            return;
        }
        downloads.execute(() -> {
            try {
                String text = WhisperModelRunner.transcribe(this, wav);
                handler.post(() -> callback.onResult("转写结果：\n" + text));
            } catch (Exception error) {
                handler.post(() -> callback.onResult("转写失败："
                        + (error.getMessage() == null ? "未知错误" : error.getMessage())));
            }
        });
    }

    void launchVisionImageOpen(DialogController.ImageResult callback) {
        pendingVisionCallback = callback;
        visionImageOpen.launch(new String[]{"image/*"});
    }

    void handleVisionImage(Uri uri) {
        DialogController.ImageResult callback = pendingVisionCallback;
        pendingVisionCallback = null;
        if (uri == null) {
            if (callback != null) callback.onResult("未选择图片。");
            return;
        }
        downloads.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("无法读取图片");
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
                if (bitmap == null) throw new IllegalStateException("图片解码失败");
                List<String> labels = MobileNetVisionClassifier.classify(this, bitmap);
                StringBuilder text = new StringBuilder();
                for (String label : labels) {
                    if (text.length() > 0) text.append("\n");
                    text.append(label);
                }
                String result = text.toString();
                handler.post(() -> callback.onResult("分类结果：\n" + result));
            } catch (Exception error) {
                handler.post(() -> callback.onResult("分类失败："
                        + (error.getMessage() == null ? "未知错误" : error.getMessage())));
            }
        });
    }

    void saveCurrentEffectTemplate() { actions.saveCurrentEffectTemplate(); }
    void showPipeline() { actions.showPipeline(); }
    void showPipelinePanel() { actions.showPipelinePanel(); }
    void completeCurrentStage() { actions.completeCurrentStage(); }
    void autoPlanEffectsAndRender() { actions.autoPlanEffectsAndRender(); }
    void runAutomaticPipeline() { actions.runAutomaticPipeline(); }
    void autoNarrateNext(List<TimelineClip> pending,int index) { actions.autoNarrateNext(pending, index); }
    void showTrackAssignment() { actions.showTrackAssignment(); }
    File runtimeLogFile() { return actions.runtimeLogFile(); }
    void logEvent(String source,String message) { actions.logEvent(source, message); }
    String readLogTail(File file,int lines) { return actions.readLogTail(file, lines); }
    void exportStructuredLog() { actions.exportStructuredLog(); }
    void confirmClearStructuredLog() { actions.confirmClearStructuredLog(); }
    void showPlatformImport() { actions.showPlatformImport(); }
    void startRemoteImport(String url,String format,boolean createProject) { actions.startRemoteImport(url, format, createProject); }
    void startRemoteImport(String url,boolean createProject) { actions.startRemoteImport(url, createProject); }
    void showSettings() { actions.showSettings(); }
    void shareDiagnostics(String report) { actions.shareDiagnostics(report); }
    void confirmPermanentDelete(ProjectRepository.ProjectInfo project) { actions.confirmPermanentDelete(project); }
    void beginProjectArchive() { actions.beginProjectArchive(); }
    void writeProjectArchive(Uri uri) { actions.writeProjectArchive(uri); }
    void restoreProjectArchive(Uri uri) { actions.restoreProjectArchive(uri); }
    static byte[] readLimited(java.io.InputStream input,int limit)throws java.io.IOException { return MainActivityActions.readLimited(input, limit); }
    void showAssetLibrary(String query) { actions.showAssetLibrary(query); }
    void addAssets(List<Uri> uris) { actions.addAssets(uris); }
    void loadAssetPreview(MobileAssetStore.AssetInfo asset,ImageView target) { actions.loadAssetPreview(asset, target); }
    void editAssetTags(MobileAssetStore.AssetInfo asset) { actions.editAssetTags(asset); }
    void confirmRemoveAsset(MobileAssetStore.AssetInfo asset) { actions.confirmRemoveAsset(asset); }
    void showEditor() { actions.showEditor(); }
    void openProject(long projectId) { actions.openProject(projectId); }
    void showProjectActions(ProjectRepository.ProjectInfo project) { actions.showProjectActions(project); }
    void showCompilations() { actions.showCompilations(); }
    void createCompilationDialog(TimelineClip pending) { actions.createCompilationDialog(pending); }
    void addCurrentClipToCompilation() { actions.addCurrentClipToCompilation(); }
    void openCompilation(ProjectRepository.CompilationInfo compilation) { actions.openCompilation(compilation); }
    void materializeCompilation(ProjectRepository.CompilationInfo compilation,List<ProjectRepository.CompilationItemInfo> items) { actions.materializeCompilation(compilation, items); }
    static String statusLabel(String status) { return MainActivityActions.statusLabel(status); }
    void addVideos(List<Uri> uris) { actions.addVideos(uris); }
    void renderTimeline() { actions.renderTimeline(); }
    void selectClip(int index) { actions.selectClip(index); }
    void stepFrame(int direction) { actions.stepFrame(direction); }
    void markBoundary(boolean inPoint) { actions.markBoundary(inPoint); }
    void showPlaybackSpeed() { actions.showPlaybackSpeed(); }
    void openRevisionPanel() { actions.openRevisionPanel(); }
    void showRevisionActions(ProjectRepository.RevisionInfo revision) { actions.showRevisionActions(revision); }
    void restoreRevision(ProjectRepository.RevisionInfo revision) { actions.restoreRevision(revision); }
    void openClipPanel() { actions.openClipPanel(); }
    void showKeyframes() { actions.showKeyframes(); }
    void showPositionKeyframes() { actions.showPositionKeyframes(); }
    void showOpacityKeyframes() { actions.showOpacityKeyframes(); }
    void showVolumeKeyframes() { actions.showVolumeKeyframes(); }
    void showSubtitleFiles() { actions.showSubtitleFiles(); }
    void editSubtitleCue(int index) { actions.editSubtitleCue(index); }
    void importSubtitleFile(Uri uri) { actions.importSubtitleFile(uri); }
    void writeSubtitleFile(Uri uri) { actions.writeSubtitleFile(uri); }
    void showClipVisualAdjustments() { actions.showClipVisualAdjustments(); }
    void openSubtitlePanel() { actions.openSubtitlePanel(); }
    void reviewCurrentClip() { actions.reviewCurrentClip(); }
    void showScriptQuality() { actions.showScriptQuality(); }
    void synthesizeNarration() { actions.synthesizeNarration(); }
    void generateNarration(TimelineClip clip,android.speech.tts.Voice voice,float speed,float pitch) { actions.generateNarration(clip, voice, speed, pitch); }
    void showTrackControls() { actions.showTrackControls(); }
    void openAssetPlacement() { actions.openAssetPlacement(); }
    void configurePlacement(MobileAssetStore.PlacementInfo placement) { actions.configurePlacement(placement); }
    void split() { actions.split(); }
    void move(int delta) { actions.move(delta); }
    void confirmDelete() { actions.confirmDelete(); }
    void removeSelected() { actions.removeSelected(); }
    void mergeRight() { actions.mergeRight(); }
    void pushHistory() { actions.pushHistory(); }
    void pushHistory(String description) { actions.pushHistory(description); }
    void undo() { actions.undo(); }
    void redo() { actions.redo(); }
    void afterHistoryChange() { actions.afterHistoryChange(); }
    static String joinCreativeText(String left,String right) { return MainActivityActions.joinCreativeText(left, right); }
    void persistProject(String reason) { actions.persistProject(reason); }
    void showExportActions(File file) { actions.showExportActions(file); }
    Uri exportUri(File file) { return actions.exportUri(file); }
    void openExportFile(File file) { actions.openExportFile(file); }
    void shareExportFile(File file) { actions.shareExportFile(file); }
    void cancelActiveExport() { actions.cancelActiveExport(); }
    void exportProject() { actions.exportProject(); }
    static boolean hasEncoder(String mime) { return MainActivityActions.hasEncoder(mime); }
    void startExport(ExportController.Preset preset) { actions.startExport(preset); }
    long readDuration(Uri uri) { return actions.readDuration(uri); }
    String displayName(Uri uri) { return actions.displayName(uri); }
    void unavailable(String message) { actions.unavailable(message); }
    void showError(String title, String message) { actions.showError(title, message); }
    TimelineClip current() { return actions.current(); }
    long totalDuration() { return actions.totalDuration(); }
    static String format(long ms) { return MainActivityActions.format(ms); }
    static String buildRuler(long total) { return MainActivityActions.buildRuler(total); }
    long value(EditText field) { return actions.value(field); }
    float floatValue(EditText field) { return actions.floatValue(field); }
    LinearLayout sheet(String title) { return actions.sheet(title); }
    LinearLayout card() { return actions.card(); }
    LinearLayout card(int color) { return actions.card(color); }
    LinearLayout column() { return actions.column(); }
    LinearLayout row() { return actions.row(); }
    TextView sectionTitle(String value) { return actions.sectionTitle(value); }
    TextView label(String value, float size, int color, boolean bold) { return actions.label(value, size, color, bold); }
    Button action(String text, int background, int foreground, View.OnClickListener listener) { return actions.action(text, background, foreground, listener); }
    Button navButton(String text, int color, View.OnClickListener listener) { return actions.navButton(text, color, listener); }
    Button iconButton(String text, View.OnClickListener listener) { return actions.iconButton(text, listener); }
    LinearLayout tool(String icon, String title, View.OnClickListener listener) { return actions.tool(icon, title, listener); }
    EditText number(String hint, long value) { return actions.number(hint, value); }
    EditText creativeInput(String hint,String value) { return actions.creativeInput(hint, value); }
    GradientDrawable shape(int color, int radius) { return actions.shape(color, radius); }
    GradientDrawable outlined(int color, int radius) { return actions.outlined(color, radius); }
    LinearLayout.LayoutParams match(int height) { return actions.match(height); }
    int dp(int value) { return actions.dp(value); }

    @Override public boolean onKeyDown(int keyCode,KeyEvent event){if(editorVisible){if(keyCode==KeyEvent.KEYCODE_J){stepFrame(-1);return true;}if(keyCode==KeyEvent.KEYCODE_K){playback.playOrPause();return true;}if(keyCode==KeyEvent.KEYCODE_L){stepFrame(1);return true;}}return super.onKeyDown(keyCode,event);}

    @Override protected void onDestroy() {
        if (exportController != null) exportController.cancelForegroundOnly();
        autoPipelineActive=false;
        if(textToSpeech!=null){textToSpeech.stop();textToSpeech.shutdown();}
        thumbnails.shutdownNow();
        if(activeDownload!=null)activeDownload.cancel(true);downloads.shutdownNow();
        player.release();
        if(projectBackups!=null)projectBackups.close();
        projectStore.close();
        super.onDestroy();
    }
}
