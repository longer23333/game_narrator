package cn.longer233.gamenarrator.mobile;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.transformer.*;
import androidx.media3.ui.PlayerView;
import java.io.File;

public final class MainActivity extends AppCompatActivity {
    private ExoPlayer player;
    private Uri source;
    private EditText start, end, subtitle;
    private CheckBox muted;
    private TextView status;
    private final EditHistory history = new EditHistory();
    private ActivityResultLauncher<String> picker;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        picker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::loadVideo);
        player = new ExoPlayer.Builder(this).build();
        setContentView(buildUi());
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this); title.setText("GameNarrator · Android 独立剪辑"); title.setTextSize(21);
        root.addView(title);
        Button choose = button("选择本地视频", v -> picker.launch("video/*")); root.addView(choose);
        PlayerView preview = new PlayerView(this); preview.setPlayer(player);
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout range = new LinearLayout(this);
        start = number("入点 ms", "0"); end = number("出点 ms", "10000");
        range.addView(start, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        range.addView(end, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); root.addView(range);
        subtitle = new EditText(this); subtitle.setHint("字幕（首版保存于编辑状态，后续加入画面烧录）"); root.addView(subtitle);
        muted = new CheckBox(this); muted.setText("静音片段"); root.addView(muted);
        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("保存", v -> save())); actions.addView(button("撤销", v -> apply(history.undo(snapshot()))));
        actions.addView(button("重做", v -> apply(history.redo(snapshot())))); root.addView(actions);
        root.addView(button("导出当前片段 MP4", v -> export()));
        status = new TextView(this); status.setText("请选择视频；所有处理均在手机本地完成。"); root.addView(status);
        return root;
    }

    private Button button(String text, android.view.View.OnClickListener action) {
        Button button = new Button(this); button.setText(text); button.setOnClickListener(action); return button;
    }
    private EditText number(String hint, String value) {
        EditText edit = new EditText(this); edit.setHint(hint); edit.setText(value); edit.setInputType(2); return edit;
    }
    private void loadVideo(Uri uri) {
        if (uri == null) return; source = uri;
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); player.play();
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == ExoPlayer.STATE_READY && player.getDuration() > 0) end.setText(String.valueOf(player.getDuration()));
            }
        });
        status.setText("视频已载入，可以调整入点、出点、静音和字幕。");
    }
    private EditSnapshot snapshot() { return new EditSnapshot(value(start), value(end), muted.isChecked(), subtitle.getText().toString()); }
    private void save() { history.push(snapshot()); status.setText("编辑状态已保存，可撤销/重做（最多 50 步）。"); }
    private void apply(EditSnapshot s) { start.setText(String.valueOf(s.startMs())); end.setText(String.valueOf(s.endMs())); muted.setChecked(s.muted()); subtitle.setText(s.subtitle()); }
    private long value(EditText field) { try { return Long.parseLong(field.getText().toString()); } catch (Exception ignored) { return 0; } }

    private void export() {
        if (source == null) { status.setText("请先选择视频。"); return; }
        EditSnapshot edit = snapshot();
        if (edit.endMs() <= edit.startMs()) { status.setText("出点必须大于入点。"); return; }
        MediaItem item = new MediaItem.Builder().setUri(source).setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(edit.startMs()).setEndPositionMs(edit.endMs()).build()).build();
        EditedMediaItem edited = new EditedMediaItem.Builder(item).setRemoveAudio(edit.muted()).build();
        File output = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GameNarrator-" + System.currentTimeMillis() + ".mp4");
        Transformer transformer = new Transformer.Builder(this).addListener(new Transformer.Listener() {
            @Override public void onCompleted(Composition composition, ExportResult result) { runOnUiThread(() -> status.setText("导出完成：" + output)); }
            @Override public void onError(Composition composition, ExportResult result, ExportException error) { runOnUiThread(() -> status.setText("导出失败：" + error.getMessage())); }
        }).build();
        status.setText("正在导出，请保持应用在前台……"); transformer.start(edited, output.getAbsolutePath());
    }

    @Override protected void onDestroy() { player.release(); super.onDestroy(); }
}
