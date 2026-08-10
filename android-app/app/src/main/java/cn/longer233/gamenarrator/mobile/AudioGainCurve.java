package cn.longer233.gamenarrator.mobile;

import java.util.List;

public final class AudioGainCurve {
    private AudioGainCurve(){ }

    public static float gainAt(List<? extends KeyframeInterpolator.Point> keyframes,long timeMs,float baseVolume,long durationMs,long fadeInMs,long fadeOutMs){
        float gain=Math.max(0f,Math.min(2f,KeyframeInterpolator.valueAt(keyframes,timeMs,baseVolume)));
        if(fadeInMs>0&&timeMs<fadeInMs)gain*=equalPower(Math.max(0f,timeMs/(float)fadeInMs));
        long fadeOutStart=Math.max(0,durationMs-fadeOutMs);
        if(fadeOutMs>0&&timeMs>fadeOutStart)gain*=equalPower(Math.max(0f,Math.min(1f,(durationMs-timeMs)/(float)fadeOutMs)));
        return Math.max(0f,Math.min(2f,gain));
    }

    private static float equalPower(float progress){return (float)Math.sin(progress*Math.PI/2d);}
}
