package cn.longer233.gamenarrator.mobile;

import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.util.Size;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.common.util.UnstableApi;
import java.util.List;

@UnstableApi
public final class MobileVideoCompositorSettings implements VideoCompositorSettings {
    private final List<MobileAssetStore.PlacementInfo> overlays;
    public MobileVideoCompositorSettings(List<MobileAssetStore.PlacementInfo> overlays){this.overlays=List.copyOf(overlays);}
    @Override public Size getOutputSize(List<Size> inputSizes){return inputSizes.isEmpty()?new Size(1280,720):inputSizes.get(0);}
    @Override public OverlaySettings getOverlaySettings(int inputId,long presentationTimeUs){
        if(inputId==0)return new StaticOverlaySettings.Builder().build();
        MobileAssetStore.PlacementInfo value=overlays.get(Math.min(inputId-1,overlays.size()-1));float x=value.visualX(),y=value.visualY(),scale=value.visualScale();
        return new StaticOverlaySettings.Builder().setBackgroundFrameAnchor(x,y).setOverlayFrameAnchor(x>=0?1f:-1f,y>=0?1f:-1f).setScale(scale,scale).setAlphaScale(.96f).build();
    }
}
