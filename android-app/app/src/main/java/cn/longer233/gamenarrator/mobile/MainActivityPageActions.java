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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@UnstableApi
public final class MainActivityPageActions {
    private final MainActivity activity;

    public MainActivityPageActions(MainActivity activity) {
        this.activity = activity;
    }

    void showHome() {
        activity.editorVisible = false;
        activity.dialogs.showHome();
    }

    void showAssetLibrary() {
        activity.showAssetLibrary("");
    }

    void showAiSettings(){
        activity.editorVisible=false;
        activity.dialogs.showAiSettings();
        activity.logEvent("settings","打开 AI 设置与模型检查");
    }

    String buildEngineReport(){
        List<MobileEngineReport.Engine> engines=new ArrayList<>();
        if(activity.ttsReady){
            List<android.speech.tts.Voice> voices=new ArrayList<>();if(activity.textToSpeech.getVoices()!=null)for(android.speech.tts.Voice voice:activity.textToSpeech.getVoices())if(!voice.isNetworkConnectionRequired())voices.add(voice);
            engines.add(new MobileEngineReport.Engine("语音合成","Android 系统 TTS",MobileEngineReport.INSTALLED,voices.size()+" 个离线音色"));
        }else engines.add(new MobileEngineReport.Engine("语音合成","Android 系统 TTS",MobileEngineReport.MISSING,"缺少中文语音包或 TTS 引擎"));
        boolean h264=activity.hasEncoder(MimeTypes.VIDEO_H264),hevc=activity.hasEncoder(MimeTypes.VIDEO_H265),aac=activity.hasEncoder(MimeTypes.AUDIO_AAC);
        engines.add(new MobileEngineReport.Engine("H.264 编码","MediaCodec",h264?MobileEngineReport.INSTALLED:MobileEngineReport.MISSING,h264?"可用":"不可用"));
        engines.add(new MobileEngineReport.Engine("HEVC 编码","MediaCodec",hevc?MobileEngineReport.INSTALLED:MobileEngineReport.MISSING,""));
        engines.add(new MobileEngineReport.Engine("AAC 编码","MediaCodec",aac?MobileEngineReport.INSTALLED:MobileEngineReport.MISSING,""));
        boolean ffmpegReady = FfmpegRunner.isReady(activity);
        engines.add(new MobileEngineReport.Engine("MOV 封装","MediaMuxer/FFmpeg",
                ffmpegReady ? MobileEngineReport.INSTALLED : MobileEngineReport.UNSUPPORTED,
                ffmpegReady ? "ffmpeg 已就绪" : "需要 ffmpeg"));
        engines.add(new MobileEngineReport.Engine("ProRes 编码","MediaCodec/FFmpeg",
                ffmpegReady ? MobileEngineReport.INSTALLED : MobileEngineReport.UNSUPPORTED,
                ffmpegReady ? "ffmpeg 已就绪" : "需要 ffmpeg"));
        MobileModelDirectory.Presence models = MobileModelDirectory.check(activity);
        MobileModelDirectory.Source whisperSource = MobileModelDirectory.source(activity, "whisper");
        MobileModelDirectory.Source visionSource = MobileModelDirectory.source(activity, "vision");
        MobileModelDirectory.Source textSource = MobileModelDirectory.source(activity, "text");
        boolean whisperReady = WhisperModelRunner.isReady(activity);
        engines.add(new MobileEngineReport.Engine("转写","端侧转写引擎",
                whisperReady ? MobileEngineReport.INSTALLED : MobileEngineReport.MISSING,
                 whisperReady ? modelSourceLabel(whisperSource) + "；whisper-cli 已就绪" : "需要 whisper-*.bin 与 whisper-cli"));
        engines.add(new MobileEngineReport.Engine("画面理解","端侧视觉模型",
                models.vision() ? MobileEngineReport.INSTALLED : MobileEngineReport.MISSING,
                 models.vision() ? modelSourceLabel(visionSource) : "需要 vision-*.onnx"));
        engines.add(new MobileEngineReport.Engine("文案生成","端侧文案模型",
                models.text() ? MobileEngineReport.INSTALLED : MobileEngineReport.MISSING,
                 models.text() ? modelSourceLabel(textSource) : "需要 text-*.onnx"));
        int projects=activity.projectStore.listProjects().size()+activity.projectStore.listArchivedProjects().size();
        int revisions=activity.projectStore.listRevisions().size(),exports=activity.projectStore.listExports().size(),assets=activity.projectStore.listAssets().size();
        File database=activity.getDatabasePath("game-narrator-mobile.db");long databaseBytes=database==null?0:database.length();
        return MobileEngineReport.build(engines,new MobileEngineReport.Usage(projects,revisions,exports,assets,databaseBytes));
    }

    private static String modelSourceLabel(MobileModelDirectory.Source source) {
        if (source == MobileModelDirectory.Source.BUNDLED) return "APK 内置模型";
        if (source == MobileModelDirectory.Source.USER_INSTALLED) return "用户安装/替换模型";
        return "模型缺失";
    }

    void showInstalledVoices(){
        List<android.speech.tts.Voice> voices=new ArrayList<>();if(activity.textToSpeech.getVoices()!=null)for(android.speech.tts.Voice voice:activity.textToSpeech.getVoices())if(!voice.isNetworkConnectionRequired())voices.add(voice);voices.sort(java.util.Comparator.comparing(android.speech.tts.Voice::getName));
        List<String> labels=new ArrayList<>();for(android.speech.tts.Voice voice:voices)labels.add(voice.getLocale().getDisplayName(Locale.CHINA)+" · "+voice.getName());
        activity.dialogs.showInstalledVoices(labels);
    }

    void showGuide(){
        activity.getSharedPreferences("guide",android.content.Context.MODE_PRIVATE).edit().putBoolean("seen_v1",true).apply();
        activity.dialogs.showGuide(BuildConfig.VERSION_NAME);
        activity.logEvent("guide","查看使用引导");
    }

    void maybeShowFirstRunGuide(){if(!activity.getSharedPreferences("guide",android.content.Context.MODE_PRIVATE).getBoolean("seen_v1",false))activity.showGuide();}

    void showReleaseNotes(){
        activity.dialogs.showReleaseNotes(BuildConfig.VERSION_NAME);
    }

    void showShotSearch(){activity.showShotSearch("");}

    void showShotSearch(String query){
        activity.editorVisible=false;
        activity.dialogs.showShotSearch(query);
        activity.logEvent("search","打开镜头文本搜索");
    }

    void openShotFromSearch(TimelineClip clip){
        activity.clips.clear();activity.clips.addAll(activity.projectStore.loadClips());
        activity.selected=-1;for(int i=0;i<activity.clips.size();i++)if(activity.clips.get(i).key().equals(clip.key())){activity.selected=i;break;}
        activity.showEditor();if(activity.selected>=0)activity.selectClip(activity.selected);else activity.status.setText("镜头已不在当前项目中。");
    }

    void showRevisionComparePicker(){
        activity.dialogs.showRevisionComparePicker();
    }

    void showRevisionDiff(ProjectRepository.RevisionInfo revision){
        activity.dialogs.showRevisionDiff(revision);
    }

    void showRevisionMergeOptions(ProjectRepository.RevisionInfo revision){
        activity.dialogs.showRevisionMergeOptions(revision);
    }

    void applyRevisionMerge(ProjectRepository.RevisionInfo revision,RevisionMerge.Mode mode){
        List<TimelineClip> merged=RevisionMerge.merge(activity.clips,activity.projectStore.loadRevision(revision.id()),mode);
        activity.timelineController.replace(merged,"合并历史版本 "+revision.id());
        activity.persistProject("合并历史版本 "+revision.id()+" ("+(mode==RevisionMerge.Mode.UNION?"只补缺失":"覆盖同名")+")");
        activity.selected=activity.clips.isEmpty()?-1:0;
        activity.logEvent("revision","合并历史版本 "+revision.id()+" "+mode.name());
        activity.status.setText("已合并历史版本；合并前状态仍可通过撤回返回。");
    }

    void showKeyframeCurveEditor(){
        activity.dialogs.showKeyframeCurveEditor();
    }

    void addKeyframeDialog(TimelineClip clip,String property){
        activity.dialogs.addKeyframeDialog(clip, property);
    }

    void previewAsset(MobileAssetStore.AssetInfo asset){
        BottomSheetDialog dialog=new BottomSheetDialog(activity);
        FrameLayout previewFrame=new FrameLayout(activity);
        if(asset.type().startsWith("image/")){
            ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(Color.BLACK);
            activity.loadAssetPreview(asset,image);
            previewFrame.addView(image,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,activity.dp(380)));
        }else if(asset.type().startsWith("video/")||asset.type().startsWith("audio/")){
            ExoPlayer previewPlayer=new ExoPlayer.Builder(activity).build();
            PlayerView playerView=new PlayerView(activity);playerView.setPlayer(previewPlayer);playerView.setUseController(true);
            previewPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(asset.uri())));previewPlayer.prepare();previewPlayer.play();
            previewFrame.addView(playerView,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,activity.dp(asset.type().startsWith("audio/")?140:380)));
            dialog.setOnDismissListener(d->previewPlayer.release());
        }else{
            previewFrame.addView(activity.label("该素材类型暂不支持预览。",14,MainActivity.MUTED,false),new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,activity.dp(80),Gravity.CENTER));
        }
        LinearLayout panel=activity.column();panel.setPadding(activity.dp(18),activity.dp(8),activity.dp(18),activity.dp(20));
        panel.addView(activity.label(asset.name(),16,MainActivity.TEXT,true));
        panel.addView(activity.label(asset.type()+" · "+(asset.tags().isBlank()?"无标签":asset.tags()),12,MainActivity.MUTED,false));
        panel.addView(previewFrame);
        panel.addView(activity.action("关闭",MainActivity.ACCENT,MainActivity.TEXT,v->dialog.dismiss()),activity.match(activity.dp(50)));
        ScrollView scroll=new ScrollView(activity);scroll.addView(panel);dialog.setContentView(scroll);dialog.show();
    }

    void deriveCoverAsset(TimelineClip clip){
        if(clip==null)return;
        File root=activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);if(root==null){activity.unavailable("没有可用的图片目录。");return;}
        File derived=new File(root,"derived");if(!derived.isDirectory()&&!derived.mkdirs()){activity.unavailable("无法创建派生目录。");return;}
        activity.thumbnails.execute(()->{
            MediaMetadataRetriever retriever=new MediaMetadataRetriever();Bitmap bitmap=null;
            try{retriever.setDataSource(activity,clip.uri());bitmap=retriever.getFrameAtTime(((clip.startMs()+clip.endMs())/2)*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);}catch(Exception ignored){}finally{try{retriever.release();}catch(java.io.IOException ignored){}}
            if(bitmap==null){activity.handler.post(()->activity.unavailable("无法从当前片段提取封面帧。"));return;}
            File output=new File(derived,DerivedAssetName.coverFileName(clip.name(),System.currentTimeMillis()));
            try(FileOutputStream stream=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}catch(java.io.IOException error){activity.handler.post(()->activity.showError("派生失败",error.getMessage()));return;}
            activity.handler.post(()->{
                try{
                    long assetId=activity.projectStore.addAsset(Uri.fromFile(output).toString(),clip.name()+" · 派生封面","image/png");
                    activity.projectStore.updateAssetTags(assetId,"派生,封面");
                    activity.logEvent("library","从片段派生封面素材");
                    Toast.makeText(activity,"派生封面已加入素材库",Toast.LENGTH_LONG).show();
                    activity.showAssetLibrary("派生");
                }catch(Exception error){activity.showError("派生失败",error.getMessage());}
            });
        });
    }

    void showEffectTemplates(){
        activity.dialogs.showEffectTemplates();
    }

    void applyEffectTemplate(ProjectRepository.EffectTemplateInfo template){
        activity.dialogs.applyEffectTemplate(template);
    }

    void beginTemplateExport(){
        try{
            activity.pendingTemplateExport=activity.projectStore.exportEffectTemplatesJson();
            activity.templateExportCreate.launch("GameNarrator-effect-templates.json");
        }catch(Exception error){activity.showError("模板导出失败",error.getMessage());}
    }

    void importTemplates(){
        activity.templateExportOpen.launch(new String[]{"application/json"});
    }

    void writeTemplateExport(Uri uri){
        if(uri==null||activity.pendingTemplateExport==null)return;
        try{ProjectStorage.write(activity.getContentResolver(),uri,activity.pendingTemplateExport);Toast.makeText(activity,"特效模板已导出",Toast.LENGTH_LONG).show();}
        catch(Exception error){activity.showError("模板导出失败",error.getMessage());}
        finally{activity.pendingTemplateExport=null;}
    }

    void restoreTemplateExport(Uri uri){
        if(uri==null)return;
        try{
            String json=ProjectStorage.read(activity.getContentResolver(),uri,ProjectStorage.MAX_ARCHIVE_BYTES);
            int count=activity.projectStore.importEffectTemplates(json);
            activity.showEffectTemplates();
            Toast.makeText(activity,"已导入 "+count+" 个特效模板",Toast.LENGTH_LONG).show();
        }catch(Exception error){activity.showError("模板导入失败",error.getMessage());}
    }

    void saveCurrentEffectTemplate(){
        activity.dialogs.saveCurrentEffectTemplate();
    }

    void showPipeline(){
        activity.editorVisible=false;
        activity.dialogs.showPipeline();
    }

    void showPipelinePanel(){
        activity.dialogs.showPipelinePanel();
    }

    void completeCurrentStage(){
        String current=activity.projectStore.pipelineStage();
        String next=PipelineStages.next(current);
        if(next==null){activity.unavailable("所有阶段已完成。");return;}
        activity.projectStore.setPipelineStage(next);
        activity.persistProject("完成阶段 "+PipelineStages.label(current));
        activity.logEvent("pipeline","完成阶段 "+current+" -> "+next);
        activity.status.setText("已进入下一阶段："+PipelineStages.label(next));
    }

    void autoPlanEffectsAndRender(){
        if(activity.clips.isEmpty()){activity.unavailable("请先导入视频。");return;}
        ProjectRepository.TaskBrief brief=activity.projectStore.taskBrief();
        String category=brief==null?"ACTION":brief.gameCategory();
        activity.pushHistory("自动规划特效");
        int count=0;
        for(TimelineClip clip:activity.clips){
            if(!clip.effectCue().isBlank())continue;
            clip.updateCreativeText(clip.subtitle(),clip.narration(),cueFor(category));
            count++;
        }
        activity.persistProject("自动规划特效 "+count+" 个分镜");
        activity.renderTimeline();
        activity.status.setText("已为 "+count+" 个分镜规划特效，开始渲染导出…");
        activity.startExport(MainActivity.EXPORT_PRESETS[1]);
    }

    private static String cueFor(String category){
        if("ACTION".equals(category)) return "高对比 快速缩放";
        if("STORY".equals(category)) return "暖色 轻微旋转";
        if("RPG".equals(category)) return "冷色 柔和缩放";
        return "高对比 旋转";
    }

    void runAutomaticPipeline(){
        if(activity.autoPipelineActive){activity.unavailable("自动流水线已在运行。");return;}
        List<TimelineClip> pending=new ArrayList<>();
        java.util.Set<String> narrated=new java.util.HashSet<>();
        for(MobileAssetStore.PlacementInfo placement:activity.projectStore.listPlacements())if("NARRATION".equals(placement.role()))narrated.add(placement.clipKey());
        for(TimelineClip clip:activity.clips){
            if(clip.subtitle().isBlank()||clip.narration().isBlank()||!narrated.contains(clip.key()))pending.add(clip);
        }
        if(pending.isEmpty()){
            activity.status.setText("自动流水线：字幕与解说已完整，检查配音并导出…");
            activity.handler.postDelayed(()->activity.startExport(MainActivity.EXPORT_PRESETS[1]),300);
            return;
        }
        activity.autoPipelineActive=true;
        activity.status.setText("自动流水线：正在为 "+pending.size()+" 个分镜补齐字幕与解说…");
        boolean textWork=false;
        for(TimelineClip clip:pending)if(clip.subtitle().isBlank()||clip.narration().isBlank()){textWork=true;break;}
        if(textWork)activity.pushHistory("自动流水线：补齐字幕与解说");
        activity.autoNarrateNext(pending,0);
    }

    void autoNarrateNext(List<TimelineClip> pending,int index){
        if(!activity.autoPipelineActive)return;
        if(index>=pending.size()){
            startTtsPhase();
            return;
        }
        TimelineClip clip=pending.get(index);
        if(clip.subtitle().isBlank()){
            if(!WhisperModelRunner.isReady(activity)){
                activity.autoNarrateNext(pending,index+1);
                return;
            }
            activity.status.setText("自动流水线：正在转写字幕 "+(index+1)+"/"+pending.size()+"（Whisper）…");
            activity.downloads.execute(()->{
                try{
                    File input=MediaInputFile.resolve(activity,clip.uri());
                    File wav=new File(activity.getCacheDir(),"auto-whisper-"+System.currentTimeMillis()+".wav");
                    FfmpegRunner.extractAudio(activity,input,wav);
                    String text=WhisperModelRunner.transcribe(activity,wav);
                    activity.handler.post(()->{
                        if(!activity.autoPipelineActive)return;
                        clip.updateCreativeText(text,clip.narration(),clip.effectCue());
                        activity.persistProject("自动流水线转写字幕 "+(index+1)+"/"+pending.size());
                        activity.status.setText("自动流水线：字幕 "+(index+1)+"/"+pending.size()+" 已转写");
                        activity.autoNarrateNext(pending,index+1);
                    });
                }catch(Exception error){
                    activity.handler.post(()->{
                        activity.autoPipelineActive=false;
                        activity.showError("自动流水线失败","转写字幕失败："+(error.getMessage()==null?"未知错误":error.getMessage()));
                    });
                }
            });
            return;
        }
        if(clip.narration().isBlank()){
            if(!MobileModelDirectory.check(activity).text()){
                activity.autoNarrateNext(pending,index+1);
                return;
            }
            activity.status.setText("自动流水线：正在生成解说 "+(index+1)+"/"+pending.size()+"（GPT-2）…");
            activity.downloads.execute(()->{
                try{
                    String prompt="A game commentary segment about "+clip.name()+". ";
                    if(!clip.subtitle().isBlank())prompt+="Scene transcript: "+clip.subtitle()+" ";
                    String text=Gpt2OnnxGenerator.generate(activity,prompt);
                    activity.handler.post(()->{
                        if(!activity.autoPipelineActive)return;
                        clip.updateCreativeText(clip.subtitle(),text,clip.effectCue());
                        activity.persistProject("自动流水线生成解说 "+(index+1)+"/"+pending.size());
                        activity.status.setText("自动流水线：解说 "+(index+1)+"/"+pending.size()+" 已生成");
                        activity.autoNarrateNext(pending,index+1);
                    });
                }catch(Exception error){
                    activity.handler.post(()->{
                        activity.autoPipelineActive=false;
                        activity.showError("自动流水线失败","生成解说文案失败："+(error.getMessage()==null?"未知错误":error.getMessage()));
                    });
                }
            });
            return;
        }
        activity.autoNarrateNext(pending,index+1);
    }

    void startTtsPhase(){
        if(!activity.autoPipelineActive)return;
        ProjectRepository.TaskBrief brief = activity.projectStore.taskBrief();
        if(brief != null && brief.storyboardReviewEnabled()){
            activity.autoPipelineActive=false;
            activity.status.setText("自动流水线：字幕与解说文案已生成，等待你检查后再继续配音与导出。");
            Toast.makeText(activity, "文案已生成并暂停，检查分镜后可再次运行自动流水线继续配音与导出", Toast.LENGTH_LONG).show();
            return;
        }
        List<TimelineClip> ttsPending=new ArrayList<>();
        List<MobileAssetStore.PlacementInfo> placements=activity.projectStore.listPlacements();
        for(TimelineClip clip:activity.clips){
            if(clip.narration().isBlank())continue;
            boolean hasNarration=false;
            for(MobileAssetStore.PlacementInfo placement:placements)if(placement.clipKey().equals(clip.key())&&"NARRATION".equals(placement.role())){hasNarration=true;break;}
            if(!hasNarration)ttsPending.add(clip);
        }
        if(ttsPending.isEmpty()){
            activity.autoPipelineActive=false;
            activity.status.setText("自动流水线：字幕与解说完成，开始导出…");
            activity.handler.postDelayed(()->activity.startExport(MainActivity.EXPORT_PRESETS[1]),400);
            return;
        }
        if(!activity.ttsReady){
            activity.autoPipelineActive=false;
            activity.showError("端侧语音不可用","字幕与解说文案已补齐，但仍有分镜需要配音：请先安装离线中文 TTS 语音包。");
            return;
        }
        activity.status.setText("自动流水线：正在为 "+ttsPending.size()+" 个分镜合成解说…");
        synthesizeNarration(ttsPending,0);
    }

    void synthesizeNarration(List<TimelineClip> pending,int index){
        if(!activity.autoPipelineActive)return;
        if(index>=pending.size()){
            activity.autoPipelineActive=false;
            activity.status.setText("自动流水线：解说完成，开始导出…");
            activity.handler.postDelayed(()->activity.startExport(MainActivity.EXPORT_PRESETS[1]),400);
            return;
        }
        TimelineClip clip=pending.get(index);
        List<android.speech.tts.Voice> voices=new ArrayList<>();if(activity.textToSpeech.getVoices()!=null)for(android.speech.tts.Voice voice:activity.textToSpeech.getVoices())if(!voice.isNetworkConnectionRequired())voices.add(voice);
        if(voices.isEmpty()){activity.autoPipelineActive=false;activity.showError("自动流水线中断","没有可用的离线音色。");return;}
        ProjectRepository.VoiceConfig config=activity.projectStore.voiceConfig(clip.key());
        android.speech.tts.Voice voice=voices.get(0);for(android.speech.tts.Voice candidate:voices)if(candidate.getName().equals(config.voiceName())){voice=candidate;break;}
        final android.speech.tts.Voice chosenVoice=voice;
        File root=activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC);if(root==null){activity.autoPipelineActive=false;activity.unavailable("设备没有可用的应用音乐目录。");return;}
        File directory=new File(root,"narration");if(!directory.isDirectory()&&!directory.mkdirs()){activity.autoPipelineActive=false;activity.unavailable("无法创建本地配音目录。");return;}
        String utterance="auto-"+clip.key()+"-"+System.currentTimeMillis();File output=new File(directory,utterance+".wav");
        activity.textToSpeech.stop();activity.textToSpeech.setVoice(voice);activity.textToSpeech.setSpeechRate(config.speed());activity.textToSpeech.setPitch(config.pitch());
        activity.textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onDone(String id){if(!activity.autoPipelineActive||!utterance.equals(id))return;activity.handler.post(()->{try{activity.pushHistory("自动流水线生成解说 "+(index+1)+"/"+pending.size());long assetId=activity.projectStore.addAsset(Uri.fromFile(output).toString(),clip.name()+" · "+chosenVoice.getName(),"audio/wav");activity.projectStore.removePlacementsForRole(clip.key(),"NARRATION");activity.projectStore.placeAsset(clip.key(),assetId,"NARRATION");activity.persistProject("自动流水线生成解说 "+(index+1)+"/"+pending.size());activity.status.setText("自动流水线：解说 "+(index+1)+"/"+pending.size());MainActivityPageActions.this.synthesizeNarration(pending,index+1);}catch(Exception error){activity.autoPipelineActive=false;activity.showError("自动流水线失败",error.getMessage());}});}
            @SuppressWarnings("deprecation") @Override public void onError(String id){handleError(id);}
            @Override public void onError(String id,int code){handleError(id);}
            private void handleError(String id){if(activity.autoPipelineActive&&utterance.equals(id)){activity.autoPipelineActive=false;activity.showError("自动流水线中断","第 "+(index+1)+" 个分镜配音失败。");}}
        });
        int result=activity.textToSpeech.synthesizeToFile(clip.narration(),new Bundle(),output,utterance);
        if(result==TextToSpeech.ERROR){activity.autoPipelineActive=false;activity.showError("自动流水线失败","系统 TTS 拒绝了合成请求。");}
    }

    void showTrackAssignment(){
        activity.dialogs.showTrackAssignment();
    }

    File runtimeLogFile(){return new File(activity.getFilesDir(),"runtime-log.jsonl");}

    void logEvent(String source,String message){try{StructuredLog.append(activity.runtimeLogFile(),System.currentTimeMillis(),"INFO",source,message);}catch(Exception ignored){}}

    String readLogTail(File file,int lines){try{List<String> values=StructuredLog.tail(file,lines);return String.join("\n",values);}catch(Exception error){return "";}}

    void exportStructuredLog(){
        try{
            File source=activity.runtimeLogFile();if(!source.isFile()){activity.unavailable("暂无结构化日志可导出。");return;}
            File root=new File(activity.getCacheDir(),"logs");if(!root.isDirectory()&&!root.mkdirs())throw new IllegalStateException("无法创建日志导出目录");
            File output=new File(root,"GameNarrator-runtime-log-"+System.currentTimeMillis()+".jsonl");
            try(FileOutputStream stream=new FileOutputStream(output)){stream.write(StructuredLog.readAll(source).getBytes(StandardCharsets.UTF_8));}
            Uri uri=FileProvider.getUriForFile(activity,BuildConfig.APPLICATION_ID+".files",output);
            Intent intent=new Intent(Intent.ACTION_SEND).setType("application/x-ndjson").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent,"分享 GameNarrator 结构化日志"));
            activity.logEvent("log","导出结构化运行日志");
        }catch(Exception error){activity.showError("日志导出失败",error.getMessage());}
    }

    void confirmClearStructuredLog(){
        activity.dialogs.confirm("清空结构化运行日志？", "只删除应用私有日志文件，不影响项目数据。", "清空", () -> {
            StructuredLog.clear(activity.runtimeLogFile());
            Toast.makeText(activity, "运行日志已清空", Toast.LENGTH_SHORT).show();
            activity.showAiSettings();
            activity.logEvent("log", "清空结构化运行日志");
        });
    }

    void showPlatformImport(){
        activity.editorVisible=false;
        activity.dialogs.showPlatformImport();
    }

    void startRemoteImport(String url,String format,boolean createProject){
        if(url==null||url.isBlank()){activity.unavailable("请粘贴媒体地址。");return;}if(activity.activeDownload!=null&&!activity.activeDownload.isDone()){activity.unavailable("已有下载任务正在运行。");return;}
        BottomSheetDialog dialog=new BottomSheetDialog(activity);LinearLayout panel=activity.sheet("解析平台媒体");TextView message=activity.label("正在解析网页媒体直链…",13,MainActivity.MUTED,false);ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);panel.addView(message);panel.addView(progress,activity.match(activity.dp(38)));panel.addView(activity.action("取消",MainActivity.DANGER,MainActivity.TEXT,v->{if(activity.activeDownload!=null)activity.activeDownload.cancel(true);dialog.dismiss();Toast.makeText(activity,"解析已取消",Toast.LENGTH_SHORT).show();}),activity.match(activity.dp(52)));dialog.setCancelable(false);dialog.setContentView(panel);dialog.show();
        activity.activeDownload=activity.downloads.submit(()->{
            try{
                String resolved=RemoteMediaImporter.resolveDirectUrl(url,format,activity.cookieSessionFor(url));
                activity.handler.post(()->dialog.dismiss());
                activity.activeDownload=null;
                activity.startRemoteImport(resolved,createProject);
            }catch(InterruptedException cancelled){Thread.currentThread().interrupt();}catch(Exception error){activity.handler.post(()->{dialog.dismiss();activity.showError("平台解析失败",error.getMessage());});}
        });
    }

    void startRemoteImport(String url,boolean createProject){
        if(url==null||url.isBlank()){activity.unavailable("请粘贴媒体直链。");return;}if(activity.activeDownload!=null&&!activity.activeDownload.isDone()){activity.unavailable("已有下载任务正在运行。");return;}
        BottomSheetDialog dialog=new BottomSheetDialog(activity);LinearLayout panel=activity.sheet("正在下载到手机");TextView message=activity.label("连接远程媒体；若有同地址断点将自动续传…",13,MainActivity.MUTED,false);ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setIndeterminate(true);panel.addView(message);panel.addView(progress,activity.match(activity.dp(38)));panel.addView(activity.action("暂停下载",MainActivity.DANGER,MainActivity.TEXT,v->{if(activity.activeDownload!=null)activity.activeDownload.cancel(true);dialog.dismiss();Toast.makeText(activity,"下载已暂停，再次导入同一地址将从断点继续",Toast.LENGTH_LONG).show();}),activity.match(activity.dp(52)));dialog.setCancelable(false);dialog.setContentView(panel);dialog.show();
        activity.activeDownload=activity.downloads.submit(()->{try{RemoteMediaImporter.Result result=RemoteMediaImporter.download(activity,url,activity.cookieSessionFor(url),(percent,done,total)->activity.handler.post(()->{if(percent>=0){progress.setIndeterminate(false);progress.setProgress(percent);}message.setText("已下载 "+MobileDiagnostics.formatBytes(done)+(total>0?" / "+MobileDiagnostics.formatBytes(total):""));}));activity.handler.post(()->{long assetId=activity.projectStore.addAsset(Uri.fromFile(result.file()).toString(),result.displayName(),result.mimeType());dialog.dismiss();if(createProject&&result.mimeType().startsWith("video/")){long duration=activity.readDuration(Uri.fromFile(result.file()));activity.projectStore.createProject(result.displayName());activity.clips.clear();activity.clips.add(new TimelineClip(Uri.fromFile(result.file()),result.displayName(),0,duration));activity.projects.saveOnly("从手机端直链导入创建项目");activity.selected=0;activity.projects.clearHistory();activity.showEditor();activity.status.setText("远程视频已下载并建立本地项目。");}else{activity.showAssetLibrary();Toast.makeText(activity,"媒体已下载到素材库",Toast.LENGTH_LONG).show();}});}catch(InterruptedException cancelled){Thread.currentThread().interrupt();}catch(Exception error){activity.handler.post(()->{dialog.dismiss();activity.showError("远程导入失败",error.getMessage());});}});
    }

    void showSettings(){
        activity.editorVisible=false;
        activity.dialogs.showSettings();
    }

    void shareDiagnostics(String report){
        try{File file=MobileDiagnostics.export(activity,report);Uri uri=FileProvider.getUriForFile(activity,BuildConfig.APPLICATION_ID+".files",file);Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(Intent.createChooser(intent,"分享 GameNarrator 诊断"));}catch(Exception error){activity.showError("无法导出诊断",error.getMessage());}
    }

    void confirmPermanentDelete(ProjectRepository.ProjectInfo project){new AlertDialog.Builder(activity).setTitle("永久删除“"+project.name()+"”？").setMessage("此操作无法撤回，将删除应用内工程、版本、导出记录和素材放置关系。手机原媒体与已经生成的 MP4 不会删除。").setNegativeButton("取消",null).setPositiveButton("永久删除",(d,w)->{activity.projectStore.permanentlyDeleteProject(project.id());activity.showSettings();}).show();}

    void beginProjectArchive(){try{activity.pendingProjectArchive=activity.projects.buildProjectArchive();activity.projectArchiveCreate.launch(ProjectStorage.safeFileName(activity.projectStore.activeProjectName())+".gnproject.json");}catch(Exception error){activity.showError("项目归档生成失败",error.getMessage());}}

    void writeProjectArchive(Uri uri){if(uri==null||activity.pendingProjectArchive==null)return;try{ProjectStorage.write(activity.getContentResolver(),uri,activity.pendingProjectArchive);Toast.makeText(activity,"项目归档已导出",Toast.LENGTH_LONG).show();}catch(Exception error){activity.showError("项目归档导出失败",error.getMessage());}finally{activity.pendingProjectArchive=null;}}

    void restoreProjectArchive(Uri uri){if(uri==null)return;try{String json=ProjectStorage.read(activity.getContentResolver(),uri,ProjectStorage.MAX_ARCHIVE_BYTES);activity.projects.restoreProjectArchive(json);activity.selected=activity.clips.isEmpty()?-1:0;activity.showEditor();if(activity.status!=null)activity.status.setText("项目归档已恢复为新项目。");}catch(Exception error){activity.showError("项目恢复失败",error.getMessage());}}

    static byte[] readLimited(java.io.InputStream input,int limit)throws java.io.IOException{java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[16*1024];int total=0,read;while((read=input.read(buffer))>=0){total+=read;if(total>limit)throw new IllegalArgumentException("文件超过允许大小 "+(limit/1024/1024)+" MiB");output.write(buffer,0,read);}return output.toByteArray();}

    void showAssetLibrary(String query) {
        activity.editorVisible=false;
        activity.dialogs.showAssetLibrary(query);
    }

    void addAssets(List<Uri> uris){
        if(uris!=null)for(Uri uri:uris){
            try{activity.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
            String type=activity.getContentResolver().getType(uri);if(type==null)type="application/octet-stream";
            activity.projectStore.addAsset(uri.toString(),activity.displayName(uri),type);
        }
        activity.logEvent("library","素材库导入完成");
        activity.showAssetLibrary();
    }

    void loadAssetPreview(MobileAssetStore.AssetInfo asset,ImageView target){
        if(asset.type().startsWith("audio/")){target.setImageResource(android.R.drawable.ic_media_play);return;}
        activity.thumbnails.execute(()->{
            Bitmap bitmap=null;Uri uri=Uri.parse(asset.uri());
            try{
                if(asset.type().startsWith("image/"))try(java.io.InputStream in=activity.getContentResolver().openInputStream(uri)){bitmap=android.graphics.BitmapFactory.decodeStream(in);}
                else{MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(activity,uri);bitmap=r.getFrameAtTime();}finally{try{r.release();}catch(java.io.IOException ignored){}}}
            }catch(Exception ignored){}
            Bitmap result=bitmap;if(result!=null)activity.handler.post(()->target.setImageBitmap(result));
        });
    }

    void editAssetTags(MobileAssetStore.AssetInfo asset){
        EditText input=new EditText(activity);input.setText(asset.tags());input.setHint("用逗号分隔标签");
        new AlertDialog.Builder(activity).setTitle("编辑素材标签").setView(input).setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{activity.projectStore.updateAssetTags(asset.id(),input.getText().toString());activity.showAssetLibrary();}).show();
    }

    void confirmRemoveAsset(MobileAssetStore.AssetInfo asset){
        new AlertDialog.Builder(activity).setTitle("移除素材记录？").setMessage("不会删除手机中的原文件；相关分镜放置也会移除。")
                .setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->{activity.projectStore.deleteAsset(asset.id());activity.showAssetLibrary();}).show();
    }

    void showEditor() {
        activity.editorVisible = true;
        activity.dialogs.showEditor();
    }

    void openProject(long projectId) {
        activity.projectStore.selectProject(projectId);
        activity.clips.clear();
        activity.clips.addAll(activity.projectStore.loadClips());
        activity.projects.clearHistory();
        activity.selected = activity.clips.isEmpty() ? -1 : 0;
        activity.showEditor();
    }

    void showProjectActions(ProjectRepository.ProjectInfo project){ activity.dialogs.showProjectActions(project); }

    void showCompilations(){
        activity.editorVisible=false;
        activity.dialogs.showCompilations();
    }

    void createCompilationDialog(TimelineClip pending){
        activity.dialogs.createCompilation(pending);
    }

    void addCurrentClipToCompilation(){
        TimelineClip clip=activity.current();if(clip==null){activity.unavailable("请先选择片段。");return;}
        activity.dialogs.showAddToCompilation(clip);
    }

    void openCompilation(ProjectRepository.CompilationInfo compilation){
        activity.editorVisible=false;
        activity.dialogs.openCompilation(compilation);
    }

    void materializeCompilation(ProjectRepository.CompilationInfo compilation,List<ProjectRepository.CompilationItemInfo> items){
        if(items.isEmpty()){activity.unavailable("合集内还没有片段。");return;}activity.projectStore.createProject(compilation.name()+" · 合集剪辑");activity.clips.clear();
        for(ProjectRepository.CompilationItemInfo item:items){TimelineClip source=item.clip();TimelineClip copy=new TimelineClip(source.uri(),source.name(),source.startMs(),source.endMs());copy.update(source.startMs(),source.endMs(),source.muted(),source.subtitle());copy.updateCreativeText(source.subtitle(),source.narration(),source.effectCue());activity.clips.add(copy);}
        activity.projects.saveOnly("从切片合集生成项目");activity.projects.clearHistory();activity.selected=0;activity.showEditor();activity.status.setText("合集已生成独立剪辑项目。");
    }

    static String statusLabel(String status) {
        if ("DRAFT".equals(status)) return "草稿";
        if ("EDITING".equals(status)) return "编辑中";
        if ("PROCESSING".equals(status)) return "处理中";
        if ("COMPLETED".equals(status)) return "已完成";
        if ("FAILED".equals(status)) return "失败";
        if ("CANCELLED".equals(status)) return "已取消";
        return status;
    }
}
