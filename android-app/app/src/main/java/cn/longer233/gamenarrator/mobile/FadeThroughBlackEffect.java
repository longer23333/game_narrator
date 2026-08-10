package cn.longer233.gamenarrator.mobile;

import androidx.media3.effect.RgbMatrix;
import androidx.media3.common.util.UnstableApi;

@UnstableApi
public final class FadeThroughBlackEffect implements RgbMatrix {
    private final long durationUs,fadeUs;
    public FadeThroughBlackEffect(long durationMs,long fadeMs){durationUs=Math.max(1,durationMs*1000);fadeUs=Math.min(durationUs/2,Math.max(1,fadeMs*1000));}
    @Override public float[] getMatrix(long presentationTimeUs,boolean useHdr){
        float factor=1f;if(presentationTimeUs<fadeUs)factor=presentationTimeUs/(float)fadeUs;else if(presentationTimeUs>durationUs-fadeUs)factor=(durationUs-presentationTimeUs)/(float)fadeUs;factor=Math.max(0f,Math.min(1f,factor));
        return new float[]{factor,0,0,0, 0,factor,0,0, 0,0,factor,0, 0,0,0,1};
    }
}
