package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * On-device TTS narration generation dialog.
 */
@UnstableApi
public final class TimelineNarrationActions {
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);

    private final Context context;
    private final DialogController ui;
    private DialogController.NarrationHost narrationHost;

    public TimelineNarrationActions(Context context, DialogController ui) {
        this.context = context;
        this.ui = ui;
    }

    public void attach(DialogController.NarrationHost host) { this.narrationHost = host; }

    public void synthesizeNarration() {
        if (narrationHost == null) return;
        TimelineClip clip = narrationHost.currentClip();
        if (clip == null) {
            narrationHost.unavailable("请先选择片段。");
            return;
        }
        if (clip.narration().isBlank()) {
            narrationHost.unavailable("请先在分镜文案中填写解说文案。");
            return;
        }
        if (!narrationHost.ttsReady()) {
            narrationHost.showErrorDialog("端侧语音不可用",
                    "手机尚未安装支持简体中文的系统 TTS 引擎或语音包。请在系统文字转语音设置中安装后重试。");
            return;
        }
        List<String> voices = narrationHost.availableOfflineVoiceNames();
        if (voices.isEmpty()) {
            narrationHost.showErrorDialog("没有离线音色",
                    "请先在系统文字转语音设置中下载离线中文语音包。");
            return;
        }
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        Spinner voicePicker = new Spinner(context);
        List<String> labels = new ArrayList<>();
        int selectedVoice = 0;
        for (int i = 0; i < voices.size(); i++) {
            String voice = voices.get(i);
            labels.add(narrationHost.voiceDisplayName(voice));
            if (voice.equals(narrationHost.configuredVoiceName(clip.key()))) selectedVoice = i;
        }
        voicePicker.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, labels));
        voicePicker.setSelection(selectedVoice);
        panel.addView(ui.label("配音音色（仅显示已安装离线音色）", 13, MUTED, true));
        panel.addView(voicePicker, ui.match(ui.dp(52)));
        float configuredSpeed = narrationHost.configuredSpeed(clip.key());
        float configuredPitch = narrationHost.configuredPitch(clip.key());
        TextView speedLabel = ui.label(String.format(Locale.CHINA, "语速 %.2f×", configuredSpeed), 14, TEXT, true);
        SeekBar speed = new SeekBar(context);
        speed.setMax(150);
        speed.setProgress(Math.round(configuredSpeed * 100) - 50);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                speedLabel.setText(String.format(Locale.CHINA, "语速 %.2f×", (value + 50) / 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        panel.addView(speedLabel);
        panel.addView(speed, ui.match(ui.dp(42)));
        TextView pitchLabel = ui.label(String.format(Locale.CHINA, "音调 %.2f×", configuredPitch), 14, TEXT, true);
        SeekBar pitch = new SeekBar(context);
        pitch.setMax(150);
        pitch.setProgress(Math.round(configuredPitch * 100) - 50);
        pitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                pitchLabel.setText(String.format(Locale.CHINA, "音调 %.2f×", (value + 50) / 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        panel.addView(pitchLabel);
        panel.addView(pitch, ui.match(ui.dp(42)));
        new AlertDialog.Builder(context).setTitle("生成 / 重新生成配音")
                .setMessage("重新生成会替换当前分镜旧解说轨，不会与旧配音叠加。")
                .setView(panel).setNegativeButton("取消", null)
                .setPositiveButton("开始生成", (d, w) -> {
                    String voice = voices.get(voicePicker.getSelectedItemPosition());
                    float rate = (speed.getProgress() + 50) / 100f;
                    float tone = (pitch.getProgress() + 50) / 100f;
                    narrationHost.saveVoiceConfig(clip.key(), voice, rate, tone);
                    narrationHost.startNarration(clip, voice, rate, tone);
                }).show();
    }
}
