package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Timeline track assignment and mute/solo dialogs.
 */
@UnstableApi
public final class TimelineTrackActions {
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);
    private static final int DANGER = Color.rgb(255, 85, 119);

    private final Context context;
    private final DialogController ui;
    private DialogController.TrackHost trackHost;

    public TimelineTrackActions(Context context, DialogController ui) {
        this.context = context;
        this.ui = ui;
    }

    public void attach(DialogController.TrackHost host) { this.trackHost = host; }

    public void showTrackAssignment() {
        if (trackHost == null) return;
        TimelineClip clip = trackHost.currentClip();
        if (clip == null) {
            trackHost.unavailable("请先选择片段。");
            return;
        }
        new AlertDialog.Builder(context).setTitle("轨道分配")
                .setItems(new String[]{"V1 主视频轨（画面 + 原声）", "V2 视频叠层轨（画面叠加，音频静音）", "A1 纯音频轨（只混入声音）"},
                        (dialog, which) -> {
                            String track = which == 0 ? "V1" : which == 1 ? "V2" : "A1";
                            trackHost.beginEdit("分配轨道 " + track);
                            clip.setTrack(track);
                            trackHost.commitEdit("分配轨道 " + track);
                            trackHost.renderTimeline();
                            trackHost.setStatus("当前片段已分配到 " + track + " 轨道。");
                        }).setNegativeButton("关闭", null).show();
    }

    public void showTrackControls() {
        if (trackHost == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("音轨静音 / 独奏");
        panel.addView(ui.label("M 静音该轨；S 独奏该轨。存在任意独奏时，只导出已独奏且未静音的音轨。", 13, MUTED, false));
        addTrackControl(panel, "原声", "ORIGINAL", Color.rgb(54, 201, 255), dialog);
        addTrackControl(panel, "解说配音", "NARRATION", Color.rgb(255, 229, 72), dialog);
        addTrackControl(panel, "音乐 / 音效", "MUSIC", Color.rgb(255, 79, 163), dialog);
        dialog.setContentView(panel);
        dialog.show();
    }

    private void addTrackControl(LinearLayout panel, String label, String type, int color, BottomSheetDialog dialog) {
        ProjectRepository.TrackState state = trackHost.trackState(type);
        LinearLayout line = ui.row();
        TextView title = ui.label(label, 15, TEXT, true);
        line.addView(title, new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        Button mute = ui.action(state.muted() ? "M 开" : "M", state.muted() ? DANGER : Color.WHITE, TEXT, v -> {
            trackHost.beginEdit("切换音轨静音 " + type);
            trackHost.updateTrackState(type, !state.muted(), state.solo());
            trackHost.commitEdit("切换音轨静音 " + type);
            dialog.dismiss();
            trackHost.refreshTrackControls();
        });
        Button solo = ui.action(state.solo() ? "S 开" : "S", state.solo() ? color : Color.WHITE, TEXT, v -> {
            trackHost.beginEdit("切换音轨独奏 " + type);
            trackHost.updateTrackState(type, state.muted(), !state.solo());
            trackHost.commitEdit("切换音轨独奏 " + type);
            dialog.dismiss();
            trackHost.refreshTrackControls();
        });
        line.addView(mute, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(46)));
        line.addView(solo, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(46)));
        panel.addView(line, ui.match(ui.dp(50)));
    }
}
