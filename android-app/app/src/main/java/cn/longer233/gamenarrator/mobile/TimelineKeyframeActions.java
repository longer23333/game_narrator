package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keyframe dialogs: transform, position, opacity, volume and curve editor.
 */
@UnstableApi
public final class TimelineKeyframeActions {
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);

    private final Context context;
    private final DialogController ui;
    private DialogController.KeyframeHost keyframeHost;

    public TimelineKeyframeActions(Context context, DialogController ui) {
        this.context = context;
        this.ui = ui;
    }

    public void attach(DialogController.KeyframeHost host) { this.keyframeHost = host; }

    public void showKeyframes() {
        if (keyframeHost == null) return;
        TimelineClip clip = keyframeHost.currentClip();
        if (clip == null) {
            keyframeHost.unavailable("请先选择片段。");
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("变换关键帧");
        long at = Math.max(0, Math.min(clip.durationMs(), keyframeHost.playbackPosition() - clip.startMs()));
        panel.addView(ui.label("当前播放头：片段内 " + keyframeHost.formatDuration(at)
                + "。相邻关键帧之间线性插值，并在 Media3 导出时逐帧计算。", 13, MUTED, false));
        Spinner property = new Spinner(context);
        property.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"缩放（百分比）", "旋转（角度）"}));
        panel.addView(property, ui.match(ui.dp(50)));
        EditText value = ui.creativeInput("当前播放头的值", "100");
        value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        panel.addView(value, ui.match(ui.dp(66)));
        panel.addView(ui.action("在播放头写入 / 覆盖关键帧", Color.rgb(255, 229, 72), TEXT, v -> {
            String type = property.getSelectedItemPosition() == 0 ? "scale" : "rotation";
            float raw = keyframeHost.floatValueOf(value);
            if ("scale".equals(type) && (raw < 25 || raw > 300)) {
                value.setError("缩放范围 25–300%");
                return;
            }
            if ("rotation".equals(type) && (raw < -180 || raw > 180)) {
                value.setError("旋转范围 -180–180°");
                return;
            }
            keyframeHost.beginEdit("设置片段变换关键帧");
            keyframeHost.saveKeyframe(clip.key(), type, at, "scale".equals(type) ? raw / 100f : raw);
            keyframeHost.commitEdit("设置片段变换关键帧");
            dialog.dismiss();
            keyframeHost.setStatus("关键帧已写入片段内 " + keyframeHost.formatDuration(at) + "。");
        }), ui.match(ui.dp(54)));
        for (String type : new String[]{"scale", "rotation"}) {
            List<ProjectRepository.KeyframeInfo> frames = keyframeHost.listKeyframes(clip.key(), type);
            panel.addView(ui.label(("scale".equals(type) ? "缩放" : "旋转") + " · " + frames.size() + " 个关键帧",
                    14, TEXT, true));
            for (ProjectRepository.KeyframeInfo frame : frames) {
                panel.addView(ui.label(keyframeHost.formatDuration(frame.timeMs()) + "  →  "
                        + ("scale".equals(type) ? Math.round(frame.value() * 100) + "%"
                        : String.format(Locale.CHINA, "%.1f°", frame.value())), 12, MUTED, false));
            }
            if (!frames.isEmpty()) {
                panel.addView(ui.action("清空" + ("scale".equals(type) ? "缩放" : "旋转") + "关键帧",
                        Color.WHITE, TEXT, v -> {
                            keyframeHost.beginEdit("清空片段变换关键帧");
                            keyframeHost.clearKeyframes(clip.key(), type);
                            keyframeHost.commitEdit("清空片段变换关键帧");
                            dialog.dismiss();
                            showKeyframes();
                        }), ui.match(ui.dp(46)));
            }
        }
        dialog.setContentView(panel);
        dialog.show();
    }

    public void showPositionKeyframes() {
        if (keyframeHost == null) return;
        TimelineClip clip = keyframeHost.currentClip();
        if (clip == null) {
            keyframeHost.unavailable("请先选择片段。");
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("位置关键帧");
        long at = Math.max(0, Math.min(clip.durationMs(), keyframeHost.playbackPosition() - clip.startMs()));
        panel.addView(ui.label("当前播放头：片段内 " + keyframeHost.formatDuration(at)
                + "。位置以画面宽高百分比表示，-100 到 100。", 13, MUTED, false));
        Spinner axis = new Spinner(context);
        axis.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"水平位置 X", "垂直位置 Y"}));
        panel.addView(axis, ui.match(ui.dp(50)));
        EditText value = ui.creativeInput("位置百分比", "0");
        value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        panel.addView(value, ui.match(ui.dp(66)));
        panel.addView(ui.action("在播放头写入 / 覆盖位置", Color.rgb(255, 229, 72), TEXT, v -> {
            float raw = keyframeHost.floatValueOf(value);
            if (raw < -100 || raw > 100) {
                value.setError("位置范围 -100 到 100");
                return;
            }
            String type = axis.getSelectedItemPosition() == 0 ? "x" : "y";
            keyframeHost.beginEdit("设置片段位置关键帧");
            keyframeHost.saveKeyframe(clip.key(), type, at, raw / 100f);
            keyframeHost.commitEdit("设置片段位置关键帧");
            dialog.dismiss();
            keyframeHost.setStatus("位置关键帧已写入片段内 " + keyframeHost.formatDuration(at) + "。");
        }), ui.match(ui.dp(54)));
        for (String type : new String[]{"x", "y"}) {
            List<ProjectRepository.KeyframeInfo> frames = keyframeHost.listKeyframes(clip.key(), type);
            panel.addView(ui.label(("x".equals(type) ? "水平 X" : "垂直 Y") + " · " + frames.size() + " 个关键帧",
                    14, TEXT, true));
            for (ProjectRepository.KeyframeInfo frame : frames) {
                panel.addView(ui.label(keyframeHost.formatDuration(frame.timeMs()) + "  →  "
                        + Math.round(frame.value() * 100) + "%", 12, MUTED, false));
            }
            if (!frames.isEmpty()) {
                panel.addView(ui.action("清空 " + type.toUpperCase(Locale.ROOT) + " 关键帧",
                        Color.WHITE, TEXT, v -> {
                            keyframeHost.beginEdit("清空片段位置关键帧");
                            keyframeHost.clearKeyframes(clip.key(), type);
                            keyframeHost.commitEdit("清空片段位置关键帧");
                            dialog.dismiss();
                            showPositionKeyframes();
                        }), ui.match(ui.dp(46)));
            }
        }
        dialog.setContentView(panel);
        dialog.show();
    }

    public void showOpacityKeyframes() {
        if (keyframeHost == null) return;
        TimelineClip clip = keyframeHost.currentClip();
        if (clip == null) {
            keyframeHost.unavailable("请先选择片段。");
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("透明度关键帧");
        long at = Math.max(0, Math.min(clip.durationMs(), keyframeHost.playbackPosition() - clip.startMs()));
        panel.addView(ui.label("当前播放头：片段内 " + keyframeHost.formatDuration(at)
                + "。0% 为完全透明，100% 为原画面；相邻点线性插值。", 13, MUTED, false));
        EditText value = ui.creativeInput("透明度百分比", "100");
        value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        panel.addView(value, ui.match(ui.dp(66)));
        panel.addView(ui.action("在播放头写入 / 覆盖透明度", Color.rgb(255, 229, 72), TEXT, v -> {
            float raw = keyframeHost.floatValueOf(value);
            if (raw < 0 || raw > 100) {
                value.setError("透明度范围 0–100%");
                return;
            }
            keyframeHost.beginEdit("设置片段透明度关键帧");
            keyframeHost.saveKeyframe(clip.key(), "opacity", at, raw / 100f);
            keyframeHost.commitEdit("设置片段透明度关键帧");
            dialog.dismiss();
            keyframeHost.setStatus("透明度关键帧已写入片段内 " + keyframeHost.formatDuration(at) + "。");
        }), ui.match(ui.dp(54)));
        List<ProjectRepository.KeyframeInfo> frames = keyframeHost.listKeyframes(clip.key(), "opacity");
        panel.addView(ui.label("透明度 · " + frames.size() + " 个关键帧", 14, TEXT, true));
        for (ProjectRepository.KeyframeInfo frame : frames) {
            panel.addView(ui.label(keyframeHost.formatDuration(frame.timeMs()) + "  →  "
                    + Math.round(frame.value() * 100) + "%", 12, MUTED, false));
        }
        if (!frames.isEmpty()) {
            panel.addView(ui.action("清空透明度关键帧", Color.WHITE, TEXT, v -> {
                keyframeHost.beginEdit("清空片段透明度关键帧");
                keyframeHost.clearKeyframes(clip.key(), "opacity");
                keyframeHost.commitEdit("清空片段透明度关键帧");
                dialog.dismiss();
                showOpacityKeyframes();
            }), ui.match(ui.dp(46)));
        }
        dialog.setContentView(panel);
        dialog.show();
    }

    public void showVolumeKeyframes() {
        if (keyframeHost == null) return;
        TimelineClip clip = keyframeHost.currentClip();
        if (clip == null) {
            keyframeHost.unavailable("请先选择片段。");
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("音量关键帧");
        long at = Math.max(0, Math.min(clip.durationMs(), keyframeHost.playbackPosition() - clip.startMs()));
        panel.addView(ui.label("当前播放头：片段内 " + keyframeHost.formatDuration(at)
                + "。0% 静音，100% 原音量，最高 200%；导出时与淡入淡出共同生效。", 13, MUTED, false));
        EditText value = ui.creativeInput("音量百分比", "100");
        value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        panel.addView(value, ui.match(ui.dp(66)));
        panel.addView(ui.action("在播放头写入 / 覆盖音量", Color.rgb(255, 229, 72), TEXT, v -> {
            float raw = keyframeHost.floatValueOf(value);
            if (raw < 0 || raw > 200) {
                value.setError("音量范围 0–200%");
                return;
            }
            keyframeHost.beginEdit("设置片段音量关键帧");
            keyframeHost.saveKeyframe(clip.key(), "volume", at, raw / 100f);
            keyframeHost.commitEdit("设置片段音量关键帧");
            dialog.dismiss();
            keyframeHost.setStatus("音量关键帧已写入片段内 " + keyframeHost.formatDuration(at) + "。");
        }), ui.match(ui.dp(54)));
        List<ProjectRepository.KeyframeInfo> frames = keyframeHost.listKeyframes(clip.key(), "volume");
        panel.addView(ui.label("音量 · " + frames.size() + " 个关键帧", 14, TEXT, true));
        for (ProjectRepository.KeyframeInfo frame : frames) {
            panel.addView(ui.label(keyframeHost.formatDuration(frame.timeMs()) + "  →  "
                    + Math.round(frame.value() * 100) + "%", 12, MUTED, false));
        }
        if (!frames.isEmpty()) {
            panel.addView(ui.action("清空音量关键帧", Color.WHITE, TEXT, v -> {
                keyframeHost.beginEdit("清空片段音量关键帧");
                keyframeHost.clearKeyframes(clip.key(), "volume");
                keyframeHost.commitEdit("清空片段音量关键帧");
                dialog.dismiss();
                showVolumeKeyframes();
            }), ui.match(ui.dp(46)));
        }
        dialog.setContentView(panel);
        dialog.show();
    }

    public void showKeyframeCurveEditor() {
        if (keyframeHost == null) return;
        TimelineClip clip = keyframeHost.currentClip();
        if (clip == null) {
            keyframeHost.unavailable("请先选择片段。");
            return;
        }
        String[] properties = {"scale", "rotation", "x", "y", "opacity", "volume"};
        String[] labels = {"缩放", "旋转", "水平位置 X", "垂直位置 Y", "透明度", "音量"};
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("关键帧曲线编辑器");
        panel.addView(ui.label("编辑关键帧的时间、数值与区间插值曲线；缓入/缓出/缓入缓出会真实作用于 Media3 导出。", 13, MUTED, false));
        Spinner property = new Spinner(context);
        property.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, labels));
        panel.addView(property, ui.match(ui.dp(50)));
        LinearLayout list = ui.column();
        List<EditText> timeFields = new ArrayList<>();
        List<EditText> valueFields = new ArrayList<>();
        List<Spinner> easingFields = new ArrayList<>();
        Runnable render = () -> {
            list.removeAllViews();
            timeFields.clear();
            valueFields.clear();
            easingFields.clear();
            String prop = properties[property.getSelectedItemPosition()];
            List<ProjectRepository.KeyframeInfo> frames = keyframeHost.listKeyframes(clip.key(), prop);
            if (frames.isEmpty()) {
                list.addView(ui.label("该属性还没有关键帧；可在下方添加。", 13, MUTED, false), ui.match(ui.dp(46)));
            }
            for (ProjectRepository.KeyframeInfo frame : frames) {
                LinearLayout row = ui.row();
                row.setGravity(Gravity.CENTER_VERTICAL);
                EditText time = ui.creativeInput("毫秒", String.valueOf(frame.timeMs()));
                time.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                EditText value = ui.creativeInput("数值", String.valueOf(frame.value()));
                value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                Spinner easing = new Spinner(context);
                easing.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                        new String[]{"线性", "缓入", "缓出", "缓入缓出"}));
                easing.setSelection(KeyframeEasing.index(frame.easing()));
                row.addView(time, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
                row.addView(value, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
                row.addView(easing, new LinearLayout.LayoutParams(ui.dp(96), ui.dp(52)));
                list.addView(row, ui.match(ui.dp(56)));
                timeFields.add(time);
                valueFields.add(value);
                easingFields.add(easing);
            }
        };
        property.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                render.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        panel.addView(list);
        panel.addView(ui.action("＋ 添加关键帧", Color.rgb(255, 79, 163), TEXT, v -> {
            dialog.dismiss();
            addKeyframeDialog(clip, properties[property.getSelectedItemPosition()]);
        }), ui.match(ui.dp(50)));
        panel.addView(ui.action("保存曲线修改", Color.rgb(255, 229, 72), TEXT, v -> {
            String prop = properties[property.getSelectedItemPosition()];
            try {
                List<ProjectRepository.KeyframeInfo> updated = new ArrayList<>();
                for (int i = 0; i < timeFields.size(); i++) {
                    long ms = Long.parseLong(timeFields.get(i).getText().toString().trim());
                    float val = Float.parseFloat(valueFields.get(i).getText().toString().trim());
                    if (ms < 0) throw new IllegalArgumentException("时间不能为负数");
                    updated.add(new ProjectRepository.KeyframeInfo(ms, val,
                            KeyframeEasing.values()[easingFields.get(i).getSelectedItemPosition()]));
                }
                updated.sort(java.util.Comparator.comparingLong(ProjectRepository.KeyframeInfo::timeMs));
                for (int i = 1; i < updated.size(); i++) {
                    if (updated.get(i - 1).timeMs() == updated.get(i).timeMs()) {
                        throw new IllegalArgumentException("关键帧时间不能重复");
                    }
                }
                keyframeHost.beginEdit("编辑" + prop + "关键帧曲线");
                keyframeHost.replaceKeyframes(clip.key(), prop, updated);
                keyframeHost.commitEdit("编辑" + prop + "关键帧曲线");
                keyframeHost.renderTimeline();
                dialog.dismiss();
                keyframeHost.setStatus("关键帧曲线已保存，导出时按曲线插值。");
            } catch (Exception error) {
                keyframeHost.showErrorDialog("无法保存关键帧", error.getMessage());
            }
        }), ui.match(ui.dp(52)));
        render.run();
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void addKeyframeDialog(TimelineClip clip, String property) {
        if (keyframeHost == null) return;
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText time = ui.number("片段内毫秒", 0);
        EditText value = ui.creativeInput("数值", "0");
        Spinner easing = new Spinner(context);
        easing.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"线性", "缓入", "缓出", "缓入缓出"}));
        panel.addView(time);
        panel.addView(value, ui.match(ui.dp(64)));
        panel.addView(easing, ui.match(ui.dp(52)));
        new AlertDialog.Builder(context).setTitle("添加关键帧").setView(panel)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", (d, w) -> {
                    try {
                        keyframeHost.beginEdit("添加" + property + "关键帧");
                        keyframeHost.saveKeyframe(clip.key(), property, keyframeHost.valueOf(time),
                                Float.parseFloat(value.getText().toString()),
                                KeyframeEasing.values()[easing.getSelectedItemPosition()]);
                        keyframeHost.commitEdit("添加" + property + "关键帧");
                        keyframeHost.setStatus("关键帧已添加，可在曲线编辑器中调整。");
                        showKeyframeCurveEditor();
                    } catch (Exception error) {
                        keyframeHost.showErrorDialog("无法添加关键帧", error.getMessage());
                    }
                }).show();
    }
}
