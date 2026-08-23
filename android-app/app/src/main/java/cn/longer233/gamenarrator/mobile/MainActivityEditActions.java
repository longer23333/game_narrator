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
public final class MainActivityEditActions {
    private final MainActivity activity;

    public MainActivityEditActions(MainActivity activity) {
        this.activity = activity;
    }

    void addVideos(List<Uri> uris) {
        if (uris == null) return;
        if (!activity.editorVisible) activity.showEditor();
        if (!uris.isEmpty()) activity.pushHistory("导入本地视频");
        int added = 0;
        for (Uri uri : uris) {
            try { activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            long duration = activity.readDuration(uri);
            if (duration > 0) { activity.clips.add(new TimelineClip(uri, activity.displayName(uri), 0, duration)); added++; }
        }
        activity.renderTimeline();
        if (activity.selected < 0 && !activity.clips.isEmpty()) activity.selectClip(0);
        activity.status.setText(added == 0 ? "没有加入可读取的视频。" : "已加入 " + added + " 个本地视频。" );
        activity.persistProject("导入本地视频");
        activity.logEvent("import","导入本地视频 " + added + " 个");
        if(added>0&&PipelineStages.index(activity.projectStore.pipelineStage())<PipelineStages.index(PipelineStages.TRIM)){activity.projectStore.setPipelineStage(PipelineStages.TRIM);activity.status.setText("素材已导入，可继续修剪镜头。");}
        if(added>0){
            try{
                ProjectRepository.TaskBrief brief=activity.projectStore.taskBrief();
                if(brief!=null&&brief.automaticGenerationEnabled()){
                    activity.handler.postDelayed(activity::runAutomaticPipeline,600);
                    activity.status.setText("已按任务设置自动启动剪辑流水线：转写→文案→配音→导出。");
                }
            }catch(Exception ignored){ }
        }
    }

    void renderTimeline() {
        activity.timelineViewController.render(activity.timeline, activity.ruler, activity.projectSummary, activity.emptyTimeline, activity.waveformView,
                activity.subtitleTrack, activity.narrationTrack, activity.effectTrack, activity.assetTrack);
    }

    void selectClip(int index) {
        if (index < 0 || index >= activity.clips.size()) return;
        activity.selected = index;
        TimelineClip clip = activity.clips.get(index);
        activity.player.setMediaItem(MediaItem.fromUri(clip.uri()));
        activity.applyCurrentPreviewEffects();
        activity.player.prepare();
        activity.player.seekTo(clip.startMs());
        activity.player.play();
        activity.renderTimeline();
        activity.status.setText("已选择：" + clip.name());
        activity.timelineViewController.loadWaveform(clip, activity.waveformView);
    }

    void stepFrame(int direction){TimelineClip clip=activity.current();if(clip==null){activity.unavailable("请先选择片段。");return;}activity.playback.stepFrame(clip.startMs(),clip.endMs(),direction);}

    void markBoundary(boolean inPoint){TimelineClip clip=activity.current();if(clip==null){activity.unavailable("请先选择片段。");return;}long at=Math.max(clip.startMs(),Math.min(clip.endMs(),activity.player.getCurrentPosition()));if(inPoint&&at>=clip.endMs()){activity.unavailable("入点必须早于出点。");return;}if(!inPoint&&at<=clip.startMs()){activity.unavailable("出点必须晚于入点。");return;}activity.pushHistory(inPoint?"标记片段入点":"标记片段出点");clip.update(inPoint?at:clip.startMs(),inPoint?clip.endMs():at,clip.muted(),clip.subtitle());activity.persistProject(inPoint?"标记片段入点":"标记片段出点");activity.renderTimeline();activity.player.seekTo(inPoint?clip.startMs():Math.max(clip.startMs(),clip.endMs()-1));activity.status.setText((inPoint?"入点":"出点")+"已标记："+activity.format(at));}

    void showPlaybackSpeed(){activity.playback.showPlaybackSpeed();}

    void openRevisionPanel() {
        activity.dialogs.openRevisionPanel();
    }

    void showRevisionActions(ProjectRepository.RevisionInfo revision){
        activity.dialogs.showRevisionActions(revision);
    }

    void restoreRevision(ProjectRepository.RevisionInfo revision){List<TimelineClip> restored=activity.projectStore.loadRevision(revision.id());if(restored.isEmpty()&&revision.clipCount()>0){activity.showError("版本恢复失败","版本片段数据不完整。");return;}activity.timelineController.replace(restored,"检出历史版本 "+revision.id());activity.selected=activity.clips.isEmpty()?-1:0;activity.persistProject("检出历史版本 "+revision.id());if(activity.selected>=0)activity.selectClip(activity.selected);else{activity.player.clearMediaItems();activity.renderTimeline();}activity.status.setText("已恢复历史版本；恢复前状态仍可通过撤回返回。");}

    void openClipPanel() {
        activity.dialogs.openClipPanel();
    }

    void showKeyframes(){ activity.dialogs.showKeyframes(); }

    void showPositionKeyframes(){ activity.dialogs.showPositionKeyframes(); }

    void showOpacityKeyframes(){ activity.dialogs.showOpacityKeyframes(); }

    void showVolumeKeyframes(){ activity.dialogs.showVolumeKeyframes(); }

    void showSubtitleFiles(){
        activity.dialogs.showSubtitleFiles();
    }

    void editSubtitleCue(int index){ activity.dialogs.editSubtitleCue(index); }

    void importSubtitleFile(Uri uri){if(uri==null)return;try(java.io.InputStream input=activity.getContentResolver().openInputStream(uri)){if(input==null)throw new IllegalStateException("无法读取字幕文件");String raw=new String(activity.readLimited(input,5*1024*1024),StandardCharsets.UTF_8);List<SubtitleFileCodec.Cue> cues=SubtitleFileCodec.parse(raw);if(cues.isEmpty())throw new IllegalArgumentException("没有识别到有效的 SRT 时间码和字幕文本");activity.pushHistory("导入 SRT 字幕");activity.projectStore.replaceSubtitleCues(cues);activity.persistProject("导入 SRT 字幕");activity.renderTimeline();activity.status.setText("已导入 "+cues.size()+" 条精确字幕，导出时按时间码烧录。");}catch(Exception error){activity.showError("SRT 字幕导入失败",error.getMessage());}}

    void writeSubtitleFile(Uri uri){if(uri==null||activity.pendingSubtitleFile==null)return;try(java.io.OutputStream output=activity.getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IllegalStateException("无法打开字幕目标");output.write(activity.pendingSubtitleFile.getBytes(StandardCharsets.UTF_8));output.flush();Toast.makeText(activity,"SRT 字幕已导出",Toast.LENGTH_LONG).show();}catch(Exception error){activity.showError("SRT 字幕导出失败",error.getMessage());}finally{activity.pendingSubtitleFile=null;}}

    void showClipVisualAdjustments(){ activity.dialogs.showClipVisualAdjustments(); }

    void importCubeLut(Uri uri) {
        String clipKey = activity.pendingLutClipKey;
        activity.pendingLutClipKey = null;
        if (uri == null || clipKey == null) return;
        File directory = new File(activity.getFilesDir(), "luts");
        if (!directory.isDirectory() && !directory.mkdirs()) { activity.showError("LUT 导入失败", "无法创建 LUT 存储目录"); return; }
        File target = new File(directory, clipKey.replaceAll("[^A-Za-z0-9._-]", "_") + ".cube");
        try (java.io.InputStream input = activity.getContentResolver().openInputStream(uri);
             java.io.OutputStream output = new java.io.FileOutputStream(target)) {
            if (input == null) throw new IllegalArgumentException("无法读取所选 LUT 文件");
            byte[] bytes = activity.readLimited(input, 16 * 1024 * 1024);
            output.write(bytes);
            try (java.io.FileReader reader = new java.io.FileReader(target)) { CubeLutParser.parse(reader); }
            ProjectRepository.ClipVisualConfig value = activity.projectStore.clipVisualConfig(clipKey);
            activity.pushHistory("导入片段 LUT");
            activity.projectStore.saveClipVisualConfig(clipKey, value.brightness(), value.contrast(), value.saturation(),
                    value.temperature(), value.hue(), value.scale(), value.rotation(), target.getAbsolutePath());
            activity.persistProject("导入片段 LUT");
            activity.applyCurrentPreviewEffects();
            activity.status.setText("LUT 已导入；预览与最终导出使用同一 Media3 GPU 变换。");
        } catch (Exception error) {
            if (target.exists()) target.delete();
            activity.showError("LUT 导入失败", error.getMessage());
        }
    }

    void openSubtitlePanel() {
        activity.dialogs.openSubtitlePanel();
    }

    void reviewCurrentClip(){ activity.dialogs.reviewCurrentClip(); }

    void showScriptQuality(){ activity.dialogs.showScriptQuality(); }

    void synthesizeNarration(){
        activity.dialogs.synthesizeNarration();
    }

    void generateNarration(TimelineClip clip,android.speech.tts.Voice voice,float speed,float pitch){
        File root=activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC);if(root==null){activity.unavailable("设备没有可用的应用音乐目录。");return;}File directory=new File(root,"narration");if(!directory.isDirectory()&&!directory.mkdirs()){activity.unavailable("无法创建本地配音目录。");return;}
        activity.textToSpeech.stop();activity.textToSpeech.setVoice(voice);activity.textToSpeech.setSpeechRate(speed);activity.textToSpeech.setPitch(pitch);String utterance="narration-"+clip.key()+"-"+System.currentTimeMillis();File output=new File(directory,utterance+".wav");activity.status.setText("正在使用 "+voice.getName()+" 生成配音…");
        activity.textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){ }
            @Override public void onDone(String id){if(!utterance.equals(id))return;activity.handler.post(()->{activity.pushHistory("重新生成端侧解说配音");long assetId=activity.projectStore.addAsset(Uri.fromFile(output).toString(),clip.name()+" · "+voice.getName(),"audio/wav");activity.projectStore.removePlacementsForRole(clip.key(),"NARRATION");activity.projectStore.placeAsset(clip.key(),assetId,"NARRATION");activity.persistProject("重新生成端侧解说配音");activity.renderTimeline();activity.status.setText("端侧配音已生成并替换当前分镜旧解说轨。");});}
            @SuppressWarnings("deprecation") @Override public void onError(String id){handleError(id);}
            @Override public void onError(String id,int code){handleError(id);}
            private void handleError(String id){if(utterance.equals(id))activity.handler.post(()->activity.showError("配音生成失败","系统 TTS 引擎未能生成音频，请检查语音包和可用空间。"));}
        });
        int result=activity.textToSpeech.synthesizeToFile(clip.narration(),new Bundle(),output,utterance);if(result==TextToSpeech.ERROR)activity.showError("配音生成失败","系统 TTS 引擎拒绝了合成请求。");
    }

    void showTrackControls(){ activity.dialogs.showTrackControls(); }

    void openAssetPlacement(){
        activity.dialogs.openAssetPlacement();
    }

    void configurePlacement(MobileAssetStore.PlacementInfo placement){
        activity.dialogs.configurePlacement(placement);
    }

    void split() {
        TimelineClip clip = activity.current();
        if (clip == null) { activity.unavailable("请先选择片段。" ); return; }
        long at = Math.max(clip.startMs(), Math.min(activity.playback.currentPosition(), clip.endMs()));
        if (!activity.timelineController.split(activity.selected, at)) return;
        activity.persistProject("刀片分割片段");
        activity.status.setText("已在 " + activity.format(at) + " 分割片段。" );
    }

    void move(int delta) {
        if (!activity.timelineController.move(activity.selected, delta)) return;
        activity.selected += delta;
        activity.persistProject("移动片段顺序");
    }

    void confirmDelete() {
        if (activity.current() == null) return;
        activity.dialogs.confirm("删除片段？", "只会从项目时间线移除，不会删除手机里的原视频。", "删除", activity::removeSelected);
    }

    void removeSelected() {
        if (!activity.timelineController.remove(activity.selected)) return;
        activity.persistProject("删除片段");
        activity.selected = activity.clips.isEmpty() ? -1 : Math.min(activity.selected, activity.clips.size() - 1);
        if (activity.selected >= 0) activity.selectClip(activity.selected); else { activity.player.clearMediaItems(); activity.renderTimeline(); activity.status.setText("时间线为空。" ); }
    }

    void mergeRight() {
        if (activity.selected < 0 || activity.selected + 1 >= activity.clips.size()) { activity.unavailable("请选择一个右侧还有片段的镜头。" ); return; }
        TimelineClip left = activity.clips.get(activity.selected);
        TimelineClip right = activity.clips.get(activity.selected + 1);
        if (!left.uri().equals(right.uri()) || left.endMs() != right.startMs()) {
            activity.unavailable("只有同一源视频中相邻的连续片段可以连接。" );
            return;
        }
        activity.pushHistory("连接右侧片段");
        activity.projectStore.copyPlacements(right.key(),left.key());
        left.update(left.startMs(), right.endMs(), left.muted() && right.muted(),
                left.subtitle().isBlank() ? right.subtitle() : left.subtitle());
        left.updateCreativeText(
                activity.joinCreativeText(left.subtitle(),right.subtitle()),
                activity.joinCreativeText(left.narration(),right.narration()),
                activity.joinCreativeText(left.effectCue(),right.effectCue()));
        activity.clips.remove(activity.selected + 1);
        activity.persistProject("连接右侧片段");
        activity.renderTimeline();
        activity.status.setText("已连接右侧连续片段。" );
    }

    void pushHistory() {
        activity.projects.beginEdit("编辑");
        persistHistorySnapshot();
    }

    void pushHistory(String description) {
        activity.projects.beginEdit(description);
        persistHistorySnapshot();
    }

    private void persistHistorySnapshot() {
        if (activity.projectHistory == null) return;
        try {
            activity.projectHistory.push(activity.projectStore.activeProjectId(), activity.projectState.capture());
        } catch (Exception ignored) {
            // Best-effort durable history; editing must not break on persistence failure.
        }
    }

    void undo() {
        if (!activity.projects.undo()) return;
        activity.afterHistoryChange();
        activity.status.setText("已撤回上一步。");
    }

    void redo() {
        if (!activity.projects.redo()) return;
        activity.afterHistoryChange();
        activity.status.setText("已恢复撤回。");
    }

    void afterHistoryChange() {
        activity.projects.saveOnly("撤回或恢复编辑");
        activity.selected = activity.clips.isEmpty() ? -1 : Math.min(Math.max(0, activity.selected), activity.clips.size() - 1);
        if (activity.selected >= 0) activity.selectClip(activity.selected); else activity.renderTimeline();
    }

    static String joinCreativeText(String left,String right){
        if(left==null||left.isBlank())return right==null?"":right;
        if(right==null||right.isBlank()||left.equals(right))return left;
        return left+" / "+right;
    }

    void persistProject(String reason) {
        activity.projects.commitEdit(reason);
        if (activity.projectBackups != null) activity.projectBackups.requestBackup();
    }

    TimelineClip current() { return activity.selected < 0 || activity.selected >= activity.clips.size() ? null : activity.clips.get(activity.selected); }

    long totalDuration() { long total = 0; for (TimelineClip clip : activity.clips) total += clip.durationMs(); return total; }

    static String format(long ms) { return String.format(Locale.CHINA, "%02d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000); }

    static String buildRuler(long total) { long step = Math.max(5000, total / 4); return "00:00        " + format(step) + "        " + format(step * 2) + "        " + format(step * 3); }

    long value(EditText field) { try { return Long.parseLong(field.getText().toString()); } catch (Exception ignored) { return 0; } }

    float floatValue(EditText field) { try { return Float.parseFloat(field.getText().toString()); } catch (Exception ignored) { return 0; } }
}
