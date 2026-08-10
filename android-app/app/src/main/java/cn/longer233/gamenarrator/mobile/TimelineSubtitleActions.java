package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;

/**
 * Project subtitle track dialogs: list, add, edit, delete, import and export.
 */
@UnstableApi
public final class TimelineSubtitleActions {
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);
    private static final int DANGER = Color.rgb(255, 85, 119);

    private final Context context;
    private final DialogController ui;
    private DialogController.SubtitleHost subtitleHost;

    public TimelineSubtitleActions(Context context, DialogController ui) {
        this.context = context;
        this.ui = ui;
    }

    public void attach(DialogController.SubtitleHost host) { this.subtitleHost = host; }

    public void showSubtitleFiles() {
        if (subtitleHost == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("项目字幕文件");
        List<ProjectRepository.SubtitleCueInfo> cues = subtitleHost.listSubtitleCues();
        panel.addView(ui.label("精确字幕轨使用项目全局时间码。导入 SRT 后，字幕只在对应时间窗口烧录；没有精确字幕的片段仍使用分镜字幕。", 13, MUTED, false));
        panel.addView(ui.label(cues.isEmpty() ? "当前没有精确字幕" : ("当前共有 " + cues.size() + " 条精确字幕"), 16, TEXT, true));
        panel.addView(ui.action("＋ 新增字幕", Color.rgb(255, 229, 72), TEXT, v -> {
            dialog.dismiss();
            editSubtitleCue(-1);
        }), ui.match(ui.dp(52)));
        panel.addView(ui.action("导入 SRT（替换当前字幕轨）", Color.rgb(54, 201, 255), TEXT, v -> {
            dialog.dismiss();
            subtitleHost.launchSubtitleFileOpen();
        }), ui.match(ui.dp(52)));
        panel.addView(ui.action("导出 SRT", Color.rgb(255, 79, 163), TEXT, v -> {
            if (cues.isEmpty()) {
                subtitleHost.unavailable("当前没有可导出的精确字幕。");
                return;
            }
            List<SubtitleFileCodec.Cue> values = new ArrayList<>();
            for (ProjectRepository.SubtitleCueInfo cue : cues) {
                values.add(new SubtitleFileCodec.Cue(cue.startMs(), cue.endMs(), cue.text()));
            }
            subtitleHost.setPendingSubtitleFile(SubtitleFileCodec.format(values));
            String safe = subtitleHost.activeProjectName().replaceAll("[^A-Za-z0-9._-]", "_");
            if (safe.isBlank()) safe = "GameNarrator-subtitles";
            dialog.dismiss();
            subtitleHost.launchSubtitleFileCreate(safe + ".srt");
        }), ui.match(ui.dp(52)));
        if (!cues.isEmpty()) {
            panel.addView(ui.action("清空精确字幕轨", DANGER, TEXT,
                    v -> ui.confirm("清空项目字幕轨？", "分镜内手工字幕不会被删除。", "清空", () -> {
                        subtitleHost.beginEdit("清空项目字幕轨");
                        subtitleHost.clearSubtitleCues();
                        subtitleHost.commitEdit("清空项目字幕轨");
                        dialog.dismiss();
                        subtitleHost.renderTimeline();
                        subtitleHost.setStatus("项目精确字幕轨已清空。");
                    })), ui.match(ui.dp(52)));
        }
        int shown = Math.min(cues.size(), 100);
        for (int i = 0; i < shown; i++) {
            ProjectRepository.SubtitleCueInfo cue = cues.get(i);
            int index = i;
            Button item = ui.action(subtitleHost.formatDuration(cue.startMs()) + " → "
                    + subtitleHost.formatDuration(cue.endMs()) + "\n" + cue.text().replace('\n', ' '),
                    Color.WHITE, TEXT, v -> {
                        dialog.dismiss();
                        editSubtitleCue(index);
                    });
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            panel.addView(item, ui.match(ui.dp(62)));
        }
        if (cues.size() > shown) {
            panel.addView(ui.label("为保证面板流畅，仅显示前 " + shown + " 条；导出仍包含全部字幕。", 12, MUTED, false));
        }
        dialog.setContentView(panel);
        dialog.show();
    }

    public void editSubtitleCue(int index) {
        if (subtitleHost == null) return;
        List<ProjectRepository.SubtitleCueInfo> current = subtitleHost.listSubtitleCues();
        ProjectRepository.SubtitleCueInfo existing = index >= 0 && index < current.size() ? current.get(index) : null;
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText start = ui.number("项目起始时间（毫秒）", existing == null ? 0 : existing.startMs());
        EditText end = ui.number("项目结束时间（毫秒）", existing == null ? 2000 : existing.endMs());
        EditText text = ui.creativeInput("字幕文本", existing == null ? "" : existing.text());
        panel.addView(start);
        panel.addView(end);
        panel.addView(text, ui.match(ui.dp(92)));
        AlertDialog editor = new AlertDialog.Builder(context)
                .setTitle(existing == null ? "新增精确字幕" : "编辑精确字幕")
                .setView(panel).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        if (existing != null) editor.setButton(AlertDialog.BUTTON_NEUTRAL, "删除", (d, w) -> { });
        editor.setOnShowListener(x -> {
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long from = ui.longValue(start);
                long to = ui.longValue(end);
                String caption = text.getText().toString().trim();
                if (from < 0 || to <= from) {
                    end.setError("结束时间必须晚于起始时间");
                    return;
                }
                if (caption.isBlank()) {
                    text.setError("字幕不能为空");
                    return;
                }
                List<SubtitleFileCodec.Cue> values = new ArrayList<>();
                for (int i = 0; i < current.size(); i++) {
                    ProjectRepository.SubtitleCueInfo cue = current.get(i);
                    if (i == index) values.add(new SubtitleFileCodec.Cue(from, to, caption));
                    else values.add(new SubtitleFileCodec.Cue(cue.startMs(), cue.endMs(), cue.text()));
                }
                if (existing == null) values.add(new SubtitleFileCodec.Cue(from, to, caption));
                values.sort(java.util.Comparator.comparingLong(SubtitleFileCodec.Cue::startMs));
                subtitleHost.beginEdit(existing == null ? "新增精确字幕" : "编辑精确字幕");
                subtitleHost.replaceSubtitleCues(values);
                subtitleHost.commitEdit(existing == null ? "新增精确字幕" : "编辑精确字幕");
                editor.dismiss();
                subtitleHost.renderTimeline();
                subtitleHost.refreshSubtitleFiles();
            });
            if (existing != null) {
                editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    List<SubtitleFileCodec.Cue> values = new ArrayList<>();
                    for (int i = 0; i < current.size(); i++) {
                        if (i == index) continue;
                        ProjectRepository.SubtitleCueInfo cue = current.get(i);
                        values.add(new SubtitleFileCodec.Cue(cue.startMs(), cue.endMs(), cue.text()));
                    }
                    subtitleHost.beginEdit("删除精确字幕");
                    subtitleHost.replaceSubtitleCues(values);
                    subtitleHost.commitEdit("删除精确字幕");
                    editor.dismiss();
                    subtitleHost.renderTimeline();
                    subtitleHost.refreshSubtitleFiles();
                });
            }
        });
        editor.show();
    }
}
