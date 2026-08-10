package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.List;
import java.util.Locale;

/**
 * Asset placement dialogs: pick assets, adjust mix/visual parameters, remove.
 */
@UnstableApi
public final class TimelinePlacementActions {
    private static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);
    private static final int DANGER = Color.rgb(255, 85, 119);

    private final Context context;
    private final DialogController ui;
    private DialogController.PlacementHost placementHost;

    public TimelinePlacementActions(Context context, DialogController ui) {
        this.context = context;
        this.ui = ui;
    }

    public void attach(DialogController.PlacementHost host) { this.placementHost = host; }

    public void openAssetPlacement() {
        if (placementHost == null) return;
        TimelineClip clip = placementHost.currentClip();
        if (clip == null) {
            placementHost.unavailable("请先选择片段。");
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout content = ui.sheet("放置本地素材");
        List<MobileAssetStore.PlacementInfo> currentPlacements = placementHost.listPlacements();
        boolean hasCurrent = false;
        for (MobileAssetStore.PlacementInfo placement : currentPlacements) {
            if (!placement.clipKey().equals(clip.key())) continue;
            if (!hasCurrent) {
                content.addView(ui.label("当前分镜", 13, MUTED, true));
                hasCurrent = true;
            }
            String mix = placement.type().startsWith("audio/")
                    ? String.format(Locale.CHINA, " · 音量 %.0f%% · 淡入/淡出 %d/%d ms",
                    placement.volume() * 100, placement.fadeInMs(), placement.fadeOutMs()) : "";
            Button placed = ui.action(placement.name() + mix, SURFACE_HIGH, TEXT, v -> {
                dialog.dismiss();
                configurePlacement(placement);
            });
            placed.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams pp = ui.match(ui.dp(58));
            pp.setMargins(0, ui.dp(6), 0, 0);
            content.addView(placed, pp);
        }
        if (hasCurrent) content.addView(ui.label("继续添加", 13, MUTED, true));
        List<MobileAssetStore.AssetInfo> assets = placementHost.listAssets();
        if (assets.isEmpty()) {
            content.addView(ui.label("素材库为空，请先导入图片、视频或音频。", 14, MUTED, false));
            content.addView(ui.action("前往素材库", Color.rgb(255, 79, 163), TEXT, v -> {
                dialog.dismiss();
                placementHost.showAssetLibraryPage();
            }), ui.match(ui.dp(52)));
        } else {
            for (MobileAssetStore.AssetInfo asset : assets) {
                Button item = ui.action(asset.name() + "\n" + asset.type(), Color.WHITE, TEXT, v -> {
                    placementHost.beginEdit("向分镜放置素材");
                    placementHost.placeAsset(clip.key(), asset.id(), "OVERLAY");
                    placementHost.commitEdit("向分镜放置素材");
                    placementHost.renderTimeline();
                    dialog.dismiss();
                    placementHost.setStatus("素材已放入当前分镜。");
                });
                item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams p = ui.match(ui.dp(62));
                p.setMargins(0, ui.dp(7), 0, 0);
                content.addView(item, p);
            }
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void configurePlacement(MobileAssetStore.PlacementInfo placement) {
        if (placementHost == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("素材参数 · " + placement.name());
        if (placement.type().startsWith("audio/")) {
            EditText volume = ui.creativeInput("音量百分比（0-200）", String.valueOf(Math.round(placement.volume() * 100)));
            EditText fadeIn = ui.creativeInput("淡入毫秒", String.valueOf(placement.fadeInMs()));
            EditText fadeOut = ui.creativeInput("淡出毫秒", String.valueOf(placement.fadeOutMs()));
            volume.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            fadeIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            fadeOut.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            panel.addView(volume, ui.match(ui.dp(68)));
            panel.addView(fadeIn, ui.match(ui.dp(68)));
            panel.addView(fadeOut, ui.match(ui.dp(68)));
            panel.addView(ui.action("保存混音参数", Color.rgb(255, 229, 72), TEXT, v -> {
                float gain = Math.max(0f, Math.min(2f, placementHost.floatValueOf(volume) / 100f));
                long maxDuration = placementHost.currentClipDurationOrMax();
                long in = Math.min(maxDuration, placementHost.valueOf(fadeIn));
                long out = Math.min(maxDuration, placementHost.valueOf(fadeOut));
                placementHost.beginEdit("调整素材混音参数");
                placementHost.updatePlacementMix(placement.id(), gain, in, out);
                placementHost.commitEdit("调整素材混音参数");
                placementHost.renderTimeline();
                dialog.dismiss();
                placementHost.setStatus("音量与淡入淡出已保存。");
            }), ui.match(ui.dp(52)));
        } else if (placement.type().startsWith("image/") || placement.type().startsWith("video/")) {
            EditText x = ui.creativeInput("水平位置（-100 到 100）", String.valueOf(Math.round(placement.visualX() * 100)));
            EditText y = ui.creativeInput("垂直位置（-100 到 100）", String.valueOf(Math.round(placement.visualY() * 100)));
            EditText scale = ui.creativeInput("画面大小（10 到 100）", String.valueOf(Math.round(placement.visualScale() * 100)));
            x.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            y.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            scale.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            panel.addView(x, ui.match(ui.dp(68)));
            panel.addView(y, ui.match(ui.dp(68)));
            panel.addView(scale, ui.match(ui.dp(68)));
            panel.addView(ui.action("保存画面参数", Color.rgb(255, 229, 72), TEXT, v -> {
                placementHost.beginEdit("调整视觉素材位置与缩放");
                placementHost.updatePlacementVisual(placement.id(),
                        placementHost.floatValueOf(x) / 100f,
                        placementHost.floatValueOf(y) / 100f,
                        placementHost.floatValueOf(scale) / 100f);
                placementHost.commitEdit("调整视觉素材位置与缩放");
                placementHost.renderTimeline();
                dialog.dismiss();
                placementHost.setStatus("素材位置与缩放已保存。");
            }), ui.match(ui.dp(52)));
        }
        panel.addView(ui.action("从分镜移除", DANGER, TEXT, v -> {
            placementHost.beginEdit("移除分镜素材");
            placementHost.removePlacement(placement.id());
            placementHost.commitEdit("移除分镜素材");
            placementHost.renderTimeline();
            dialog.dismiss();
        }), ui.match(ui.dp(52)));
        dialog.setContentView(panel);
        dialog.show();
    }
}
