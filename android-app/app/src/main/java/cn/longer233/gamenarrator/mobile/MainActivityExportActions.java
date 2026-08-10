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
public final class MainActivityExportActions {
    private final MainActivity activity;

    public MainActivityExportActions(MainActivity activity) {
        this.activity = activity;
    }

    void exportSingleClip(TimelineClip clip){
        if(clip==null)return;if(activity.exportController.isActive()){activity.unavailable("已有导出任务正在运行。");return;}
        List<ExportController.Preset> available=new ArrayList<>();Collections.addAll(available,MainActivity.EXPORT_PRESETS);
        if (FfmpegRunner.isReady(activity)) {
            available.add(new ExportController.Preset("MOV · H.264（FFmpeg）",8_000_000,1920,1080,30,MimeTypes.VIDEO_H264,"MOV"));
            available.add(new ExportController.Preset("ProRes 422 · MOV（FFmpeg）",16_000_000,1920,1080,30,MimeTypes.VIDEO_H264,"PRO_RES"));
            available.add(new ExportController.Preset("电影感曲线 · H.264（FFmpeg）",8_000_000,1920,1080,30,
                    MimeTypes.VIDEO_H264,"FILTER","curves=preset=medium_contrast"));
        }
        String[] choices=new String[available.size()];
        for(int i=0;i<available.size();i++)choices[i]=available.get(i).label;
        new AlertDialog.Builder(activity).setTitle("导出当前镜头").setMessage("独立导出该片段的画面、字幕、素材与声音效果；MOV/ProRes 通过内置 FFmpeg 支持。")
                .setItems(choices,(d,which)->activity.startSingleClipExport(clip,available.get(which)))
                .setNegativeButton("取消",null).show();
    }

    void startSingleClipExport(TimelineClip clip, ExportController.Preset preset){
        activity.exportController.startClipExport(clip, preset);
    }

    void showExportActions(File file) {
        activity.dialogs.showExportActions(file);
    }

    Uri exportUri(File file) {
        return activity.dialogs.exportUri(file);
    }

    void openExportFile(File file) {
        activity.dialogs.openExportFile(file);
    }

    void shareExportFile(File file) {
        activity.dialogs.shareExportFile(file);
    }

    void cancelActiveExport() {
        activity.exportController.cancel();
    }

    void exportProject() {
        if (activity.clips.isEmpty()) { activity.unavailable("请先导入视频。" ); return; }
        if (activity.exportController.isActive()) { activity.unavailable("已有导出任务正在运行。"); return; }
        List<ExportController.Preset> available=new ArrayList<>();Collections.addAll(available,MainActivity.EXPORT_PRESETS);
        if(activity.hasEncoder(MimeTypes.VIDEO_H265))available.add(new ExportController.Preset("高压缩高清 · 1080p / 原帧率 / HEVC",6_000_000,1920,1080,0,MimeTypes.VIDEO_H265));
        available.add(new ExportController.Preset("竖屏平台 · 1080×1920 / 30 FPS / H.264",8_000_000,1080,1920,30,MimeTypes.VIDEO_H264));
        available.add(new ExportController.Preset("横屏平台 · 1920×1080 / 60 FPS / H.264",12_000_000,1920,1080,60,MimeTypes.VIDEO_H264));
        if (FfmpegRunner.isReady(activity)) {
            available.add(new ExportController.Preset("MOV · H.264（FFmpeg）",8_000_000,1920,1080,30,MimeTypes.VIDEO_H264,"MOV"));
            available.add(new ExportController.Preset("ProRes 422 · MOV（FFmpeg）",16_000_000,1920,1080,30,MimeTypes.VIDEO_H264,"PRO_RES"));
            available.add(new ExportController.Preset("电影感曲线 · H.264（FFmpeg）",8_000_000,1920,1080,30,
                    MimeTypes.VIDEO_H264,"FILTER","curves=preset=medium_contrast"));
        }
        String[] choices = new String[available.size()+1];
        for (int i = 0; i < available.size(); i++) choices[i] = available.get(i).label;
        choices[choices.length-1]="自定义分辨率、帧率与码率";
        new AlertDialog.Builder(activity).setTitle("选择导出预设")
                .setMessage("MOV/ProRes 通过内置 FFmpeg 支持，仅在实际就绪时显示。")
                .setItems(choices, (dialog, which) -> {if(which<available.size())activity.startExport(available.get(which));else activity.dialogs.showCustomExport();})
                .setNegativeButton("取消", null).show();
    }

    static boolean hasEncoder(String mime){for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos())if(codec.isEncoder())for(String type:codec.getSupportedTypes())if(mime.equalsIgnoreCase(type))return true;return false;}

    void startExport(ExportController.Preset preset) {
        if (preset.ffmpegKind != null) {
            activity.exportController.startProjectExport(preset);
        } else {
            activity.exportController.startBackgroundProjectExport(preset);
        }
    }

    long readDuration(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(activity, uri);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception error) { activity.showError("无法读取视频", error.getMessage()); return 0; }
        finally { try { retriever.release(); } catch (java.io.IOException ignored) { } }
    }

    String displayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) { }
        return "视频片段";
    }
}
