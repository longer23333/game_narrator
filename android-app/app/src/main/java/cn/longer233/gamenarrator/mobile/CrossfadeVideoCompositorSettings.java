package cn.longer233.gamenarrator.mobile;

import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.StaticOverlaySettings;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public final class CrossfadeVideoCompositorSettings implements VideoCompositorSettings {
    private final List<MobileAssetStore.PlacementInfo> overlays;
    private final List<Integer> crossfadeInputIds;
    private final List<Long> fadeStartsUs;
    private final List<Long> fadeDurationsUs;

    public CrossfadeVideoCompositorSettings(List<MobileAssetStore.PlacementInfo> overlays,
                                            List<Integer> crossfadeInputIds,
                                            List<Long> fadeStartsUs,
                                            List<Long> fadeDurationsUs) {
        this.overlays = overlays == null ? List.of() : List.copyOf(overlays);
        this.crossfadeInputIds = crossfadeInputIds == null ? List.of() : List.copyOf(crossfadeInputIds);
        this.fadeStartsUs = fadeStartsUs == null ? List.of() : List.copyOf(fadeStartsUs);
        this.fadeDurationsUs = fadeDurationsUs == null ? List.of() : List.copyOf(fadeDurationsUs);
    }

    @Override public Size getOutputSize(List<Size> inputSizes) {
        return inputSizes.isEmpty() ? new Size(1280, 720) : inputSizes.get(0);
    }

    @Override public OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs) {
        if (inputId == 0) return new StaticOverlaySettings.Builder().build();
        int crossIndex = crossfadeInputIds.indexOf(inputId);
        if (crossIndex >= 0) {
            long startUs = fadeStartsUs.get(Math.min(crossIndex, fadeStartsUs.size() - 1));
            long durationUs = fadeDurationsUs.get(Math.min(crossIndex, fadeDurationsUs.size() - 1));
            float alpha = CrossfadeCurve.alpha(presentationTimeUs, startUs, durationUs);
            return new StaticOverlaySettings.Builder().setAlphaScale(alpha).build();
        }
        if (overlays.isEmpty() || inputId > overlays.size()) {
            return new StaticOverlaySettings.Builder().build();
        }
        MobileAssetStore.PlacementInfo value = overlays.get(Math.min(inputId - 1, overlays.size() - 1));
        float x = value.visualX();
        float y = value.visualY();
        float scale = value.visualScale();
        return new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(x, y)
                .setOverlayFrameAnchor(x >= 0 ? 1f : -1f, y >= 0 ? 1f : -1f)
                .setScale(scale, scale)
                .setAlphaScale(.96f)
                .build();
    }
}
