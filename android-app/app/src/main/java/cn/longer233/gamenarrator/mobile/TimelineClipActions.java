package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Per-clip edit dialogs: trim, audio peak analysis, volume, visual adjustments.
 */
@UnstableApi
public final class TimelineClipActions {
    private static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);

    private final Context context;
    private final DialogController ui;
    private DialogController.EditingHost editingHost;

    public TimelineClipActions(Context context, DialogController ui) {
        this.context = context;
        this.ui = ui;
    }

    public void attach(DialogController.EditingHost host) { this.editingHost = host; }

    public void openClipPanel() {
        if (editingHost == null) return;
        TimelineClip clip = editingHost.currentClip();
        if (clip == null) {
            editingHost.unavailable("请先选择片段。");
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("裁剪与声音");
        EditText start = ui.number("入点（毫秒）", clip.startMs());
        EditText end = ui.number("出点（毫秒）", clip.endMs());
        ProjectRepository.ClipAudioConfig audio = editingHost.clipAudioConfig(clip.key());
        EditText volume = ui.number("原声音量百分比（0–200）", Math.round(audio.volume() * 100));
        EditText fadeIn = ui.number("原声淡入（毫秒）", audio.fadeInMs());
        EditText fadeOut = ui.number("原声淡出（毫秒）", audio.fadeOutMs());
        CheckBox mute = new CheckBox(context);
        mute.setText("片段静音");
        mute.setTextColor(TEXT);
        mute.setChecked(clip.muted());
        TextView peakStatus = ui.label("尚未分析真实 PCM 峰值。", 12, MUTED, false);
        final AudioPeakAnalyzer.Result[] peakResult = {null};
        Button normalize = ui.action("应用安全峰值音量", SURFACE_HIGH, TEXT, v -> {
            if (peakResult[0] != null) {
                volume.setText(String.valueOf(Math.round(peakResult[0].suggestedGain() * 100)));
                peakStatus.setText("已填入安全音量，点“应用修改”后生效。");
            }
        });
        normalize.setEnabled(false);
        Button analyze = ui.action("分析音频峰值 / 静音", Color.rgb(54, 201, 255), TEXT, v -> {
            peakStatus.setText("正在解码当前片段 PCM 音频…");
            editingHost.thumbnails().execute(() -> {
                float[] points = AudioWaveformExtractor.extract(context, clip.uri(), clip.startMs(), clip.endMs(), 512);
                AudioPeakAnalyzer.Result result = AudioPeakAnalyzer.analyze(points);
                editingHost.handler().post(() -> {
                    peakResult[0] = result;
                    normalize.setEnabled(result.peak() > .001f);
                    long bucketMs = Math.max(1, clip.durationMs() / Math.max(1, result.total()));
                    String condition = result.overloaded() ? "检测到持续过载"
                            : result.mostlySilent() ? "大部分区间接近静音" : "峰值正常";
                    peakStatus.setText(String.format(Locale.CHINA,
                            "%s · 峰值 %.1f%% · 过载约 %d ms · 最长静音约 %d ms",
                            condition, result.peak() * 100, result.longestOverload() * bucketMs,
                            result.longestSilence() * bucketMs));
                });
            });
        });
        panel.addView(start);
        panel.addView(end);
        panel.addView(volume);
        panel.addView(fadeIn);
        panel.addView(fadeOut);
        panel.addView(mute);
        panel.addView(analyze, ui.match(ui.dp(48)));
        panel.addView(peakStatus);
        panel.addView(normalize, ui.match(ui.dp(48)));
        TextView visionResult = ui.label("尚未识别画面。", 12, MUTED, false);
        panel.addView(ui.action("识别当前帧画面（端侧）", Color.rgb(54, 201, 255), TEXT, v -> {
            visionResult.setText("正在识别…");
            editingHost.thumbnails().execute(() -> {
                Bitmap frame = null;
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(context, clip.uri());
                    frame = retriever.getFrameAtTime(clip.startMs() * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                } catch (Exception ignored) {
                    frame = null;
                } finally {
                    try { retriever.release(); } catch (Exception ignored) { }
                }
                LocalVisionAnalyzer.label(frame, new LocalVisionAnalyzer.Listener() {
                    @Override public void onResult(List<String> labels) {
                        editingHost.handler().post(() -> visionResult.setText(labels.isEmpty()
                                ? "没有识别到标签。" : "标签：" + String.join("、", labels)));
                    }
                    @Override public void onError(String message) {
                        editingHost.handler().post(() -> visionResult.setText("识别失败：" + message));
                    }
                });
            });
        }), ui.match(ui.dp(48)));
        panel.addView(visionResult);
        if (WhisperModelRunner.isReady(context)) {
            TextView whisperStatus = ui.label("Whisper 会从片段抽出 WAV 后离线转写，结果写入字幕。", 12, MUTED, false);
            panel.addView(ui.action("AI 转写字幕（Whisper）", Color.rgb(54, 201, 255), TEXT, v -> {
                whisperStatus.setText("正在抽音频并转写…（手机端较慢）");
                editingHost.thumbnails().execute(() -> {
                    try {
                        File input = MediaInputFile.resolve(context, clip.uri());
                        File wav = new File(context.getCacheDir(),
                                "whisper-clip-" + System.currentTimeMillis() + ".wav");
                        FfmpegRunner.extractAudio(context, input, wav);
                        String text = WhisperModelRunner.transcribe(context, wav);
                        editingHost.handler().post(() -> {
                            clip.updateCreativeText(text, clip.narration(), clip.effectCue());
                            editingHost.beginEdit("AI 转写字幕");
                            editingHost.commitEdit("AI 转写字幕");
                            String preview = text.length() > 120 ? text.substring(0, 120) + "…" : text;
                            whisperStatus.setText("已写入字幕：" + preview);
                        });
                    } catch (Exception error) {
                        String message = error.getMessage() == null ? "未知错误" : error.getMessage();
                        editingHost.handler().post(() -> whisperStatus.setText("转写失败：" + message));
                    }
                });
            }), ui.match(ui.dp(48)));
            panel.addView(whisperStatus);
        }
        if (MobileModelDirectory.check(context).text()) {
            TextView gptStatus = ui.label("GPT-2 会根据片段名称离线生成英文解说文案，结果写入解说轨。", 12, MUTED, false);
            panel.addView(ui.action("AI 生成分镜文案（GPT-2）", Color.rgb(255, 229, 72), TEXT, v -> {
                gptStatus.setText("正在本地生成…（手机端较慢）");
                editingHost.thumbnails().execute(() -> {
                    try {
                        String prompt = "A game commentary segment about " + clip.name() + ". ";
                        String text = Gpt2OnnxGenerator.generate(context, prompt);
                        editingHost.handler().post(() -> {
                            clip.updateCreativeText(clip.subtitle(), text, clip.effectCue());
                            editingHost.beginEdit("AI 生成分镜文案");
                            editingHost.commitEdit("AI 生成分镜文案");
                            String preview = text.length() > 120 ? text.substring(0, 120) + "…" : text;
                            gptStatus.setText("已生成解说：" + preview);
                        });
                    } catch (Exception error) {
                        String message = error.getMessage() == null ? "未知错误" : error.getMessage();
                        editingHost.handler().post(() -> gptStatus.setText("生成失败：" + message));
                    }
                });
            }), ui.match(ui.dp(48)));
            panel.addView(gptStatus);
        }
        panel.addView(ui.action("导出当前镜头为独立视频", Color.rgb(255, 79, 163), TEXT, v -> {
            dialog.dismiss();
            editingHost.exportSingleClip(clip);
        }), ui.match(ui.dp(48)));
        panel.addView(ui.action("派生封面到素材库", Color.rgb(54, 201, 255), TEXT, v -> {
            dialog.dismiss();
            editingHost.deriveCoverAsset(clip);
        }), ui.match(ui.dp(48)));
        panel.addView(ui.action("应用修改", Color.rgb(255, 229, 72), TEXT, v -> {
            long from = editingHost.valueOf(start);
            long to = editingHost.valueOf(end);
            if (from < 0 || to <= from) {
                start.setError("时间范围无效");
                return;
            }
            long volumePercent = editingHost.valueOf(volume);
            long in = editingHost.valueOf(fadeIn);
            long out = editingHost.valueOf(fadeOut);
            long duration = to - from;
            if (volumePercent > 200) {
                volume.setError("音量必须在 0–200% 之间");
                return;
            }
            if (in > duration || out > duration) {
                fadeOut.setError("淡入和淡出不能超过片段时长");
                return;
            }
            editingHost.beginEdit("修改片段起止时间和声音");
            clip.update(from, to, mute.isChecked(), clip.subtitle());
            editingHost.saveClipAudioConfig(clip.key(), volumePercent / 100f, in, out);
            editingHost.commitEdit("修改片段起止时间和声音");
            editingHost.seekTo(from);
            editingHost.renderTimeline();
            dialog.dismiss();
            editingHost.setStatus("裁剪修改已应用。");
        }), ui.match(ui.dp(54)));
        dialog.setContentView(panel);
        dialog.show();
    }

    public void showClipVisualAdjustments() {
        if (editingHost == null) return;
        TimelineClip clip = editingHost.currentClip();
        if (clip == null) {
            editingHost.unavailable("请先选择片段。");
            return;
        }
        ProjectRepository.ClipVisualConfig config = editingHost.clipVisualConfig(clip.key());
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("画面调整");
        panel.addView(ui.label("结构化参数通过 Media3 GPU 效果真实导出；重置为 0 / 100% 即恢复原画面。", 13, MUTED, false));
        panel.addView(ui.label(config.lutPath().isBlank() ? "LUT：未使用" : "LUT：" + new java.io.File(config.lutPath()).getName(), 13, MUTED, false));
        panel.addView(ui.action("导入 .cube LUT", Color.rgb(54, 201, 255), TEXT, v -> {
            dialog.dismiss();
            editingHost.launchLutFileOpen(clip.key());
        }), ui.match(ui.dp(48)));
        if (!config.lutPath().isBlank()) panel.addView(ui.action("移除 LUT", Color.WHITE, TEXT, v -> {
            editingHost.beginEdit("移除片段 LUT");
            editingHost.saveClipLut(clip.key(), "");
            editingHost.commitEdit("移除片段 LUT");
            editingHost.applyPreviewEffects();
            dialog.dismiss();
            editingHost.setStatus("LUT 已移除，预览已恢复基础画面参数。");
        }), ui.match(ui.dp(46)));
        EditText brightness = ui.creativeInput("亮度（-100 到 100）", String.valueOf(Math.round(config.brightness() * 100)));
        EditText contrast = ui.creativeInput("对比度（-100 到 100）", String.valueOf(Math.round(config.contrast() * 100)));
        EditText saturation = ui.creativeInput("饱和度（-100 到 100）", String.valueOf(Math.round(config.saturation())));
        EditText temperature = ui.creativeInput("色温（-100 冷 到 100 暖）", String.valueOf(Math.round(config.temperature())));
        EditText hue = ui.creativeInput("色相（-180° 到 180°）", String.valueOf(Math.round(config.hue())));
        EditText scale = ui.creativeInput("缩放（25% 到 300%）", String.valueOf(Math.round(config.scale() * 100)));
        EditText rotation = ui.creativeInput("旋转（-180° 到 180°）", String.valueOf(Math.round(config.rotation())));
        for (EditText field : new EditText[]{brightness, contrast, saturation, temperature, hue, scale, rotation}) {
            field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        for (EditText field : new EditText[]{brightness, contrast, saturation, temperature, hue, scale, rotation}) {
            panel.addView(field, ui.match(ui.dp(66)));
        }
        panel.addView(ui.action("保存画面调整", Color.rgb(255, 229, 72), TEXT, v -> {
            float b = editingHost.floatValueOf(brightness);
            float c = editingHost.floatValueOf(contrast);
            float sat = editingHost.floatValueOf(saturation);
            float temp = editingHost.floatValueOf(temperature);
            float h = editingHost.floatValueOf(hue);
            float s = editingHost.floatValueOf(scale);
            float r = editingHost.floatValueOf(rotation);
            if (b < -100 || b > 100) { brightness.setError("范围 -100 到 100"); return; }
            if (c < -100 || c > 100) { contrast.setError("范围 -100 到 100"); return; }
            if (sat < -100 || sat > 100) { saturation.setError("范围 -100 到 100"); return; }
            if (temp < -100 || temp > 100) { temperature.setError("范围 -100 到 100"); return; }
            if (h < -180 || h > 180) { hue.setError("范围 -180 到 180"); return; }
            if (s < 25 || s > 300) { scale.setError("范围 25 到 300"); return; }
            if (r < -180 || r > 180) { rotation.setError("范围 -180 到 180"); return; }
            editingHost.beginEdit("调整片段画面参数");
            editingHost.saveClipVisualConfig(clip.key(), b / 100f, c / 100f, sat, temp, h, s / 100f, r);
            editingHost.commitEdit("调整片段画面参数");
            dialog.dismiss();
            editingHost.renderTimeline();
            editingHost.applyPreviewEffects();
            editingHost.setStatus("画面调整已保存，将在导出时通过 GPU 应用。");
        }), ui.match(ui.dp(54)));
        panel.addView(ui.action("重置画面参数", Color.WHITE, TEXT, v -> {
            editingHost.beginEdit("重置片段画面参数");
            editingHost.saveClipVisualConfig(clip.key(), 0, 0, 0, 0, 0, 1, 0);
            editingHost.saveClipLut(clip.key(), "");
            editingHost.commitEdit("重置片段画面参数");
            dialog.dismiss();
            editingHost.setStatus("画面参数已恢复默认。");
            editingHost.applyPreviewEffects();
        }), ui.match(ui.dp(50)));
        dialog.setContentView(panel);
        dialog.show();
    }

}
