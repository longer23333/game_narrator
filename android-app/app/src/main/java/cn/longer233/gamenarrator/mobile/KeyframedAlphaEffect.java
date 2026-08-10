package cn.longer233.gamenarrator.mobile;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.RgbMatrix;
import java.util.List;

@UnstableApi
public final class KeyframedAlphaEffect implements RgbMatrix {
    private final List<ProjectRepository.KeyframeInfo> frames;
    public KeyframedAlphaEffect(List<ProjectRepository.KeyframeInfo> frames){this.frames=frames;}
    @Override public float[] getMatrix(long presentationTimeUs,boolean useHdr){float alpha=Math.max(0f,Math.min(1f,KeyframeInterpolator.valueAt(frames,Math.max(0,presentationTimeUs/1000),1f)));return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,alpha};}
    @Override public boolean isNoOp(int inputWidth,int inputHeight){return frames.isEmpty();}
}
