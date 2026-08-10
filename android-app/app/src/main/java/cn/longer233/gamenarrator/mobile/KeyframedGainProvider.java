package cn.longer233.gamenarrator.mobile;

import androidx.media3.common.C;
import androidx.media3.common.audio.GainProcessor;
import androidx.media3.common.util.UnstableApi;
import java.util.List;

@UnstableApi public final class KeyframedGainProvider implements GainProcessor.GainProvider {
    private final List<ProjectRepository.KeyframeInfo> keyframes;
    private final float baseVolume;
    private final long durationMs;
    private final long fadeInMs;
    private final long fadeOutMs;

    public KeyframedGainProvider(List<ProjectRepository.KeyframeInfo> keyframes,float baseVolume,long durationMs,long fadeInMs,long fadeOutMs){this.keyframes=keyframes;this.baseVolume=baseVolume;this.durationMs=durationMs;this.fadeInMs=Math.min(durationMs,fadeInMs);this.fadeOutMs=Math.min(durationMs,fadeOutMs);}
    @Override public float getGainFactorAtSamplePosition(long samplePosition,int sampleRate){return AudioGainCurve.gainAt(keyframes,samplePosition*1000L/sampleRate,baseVolume,durationMs,fadeInMs,fadeOutMs);}
    @Override public long isUnityUntil(long samplePosition,int sampleRate){return Math.abs(getGainFactorAtSamplePosition(samplePosition,sampleRate)-1f)<.0001f?samplePosition+1:C.TIME_UNSET;}
}
