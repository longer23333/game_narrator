package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.content.Context;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.Locale;
import java.util.function.Consumer;

@UnstableApi
public final class PlaybackController {
    private final Context context;
    private final ExoPlayer player;
    private final Consumer<String> status;

    public PlaybackController(Context context, ExoPlayer player, Consumer<String> status) {
        this.context = context;
        this.player = player;
        this.status = status;
    }

    public void playOrPause() {
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    public void stepFrame(long clipStartMs, long clipEndMs, int direction) {
        player.pause();
        Format format = player.getVideoFormat();
        float rate = format == null ? 0 : format.frameRate;
        long step = rate > 1 ? Math.max(1, Math.round(1000f / rate)) : 33;
        long target = Math.max(clipStartMs, Math.min(clipEndMs - 1, player.getCurrentPosition() + direction * step));
        player.seekTo(target);
        status.accept((direction < 0 ? "上一帧 · " : "下一帧 · ") + formatMs(target));
    }

    public void showPlaybackSpeed() {
        String[] labels = {"0.5×", "1.0×", "1.25×", "1.5×", "2.0×"};
        float[] values = {.5f, 1f, 1.25f, 1.5f, 2f};
        new AlertDialog.Builder(context)
                .setTitle("预览播放速度")
                .setItems(labels, (dialog, which) -> {
                    player.setPlaybackSpeed(values[which]);
                    player.play();
                    status.accept("预览速度 " + labels[which] + "（不改变导出时长）");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public void seekTo(long positionMs) {
        player.seekTo(positionMs);
    }

    public long currentPosition() {
        return player.getCurrentPosition();
    }

    private static String formatMs(long ms) {
        return String.format(Locale.CHINA, "%02d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000);
    }
}
