package cn.longer233.gamenarrator.mobile;

import java.util.List;

public final class KeyframeInterpolator {
    private KeyframeInterpolator(){ }
    public interface Point {
        long timeMs();
        float value();
        default String easing() { return KeyframeEasing.LINEAR; }
    }
    public static float valueAt(List<? extends Point> points,long timeMs,float fallback){if(points==null||points.isEmpty())return fallback;if(timeMs<=points.get(0).timeMs())return points.get(0).value();for(int i=1;i<points.size();i++){Point right=points.get(i);if(timeMs<=right.timeMs()){Point left=points.get(i-1);long span=Math.max(1,right.timeMs()-left.timeMs());float progress=Math.max(0f,Math.min(1f,(timeMs-left.timeMs())/(float)span));return left.value()+(right.value()-left.value())*KeyframeEasing.apply(right.easing(),progress);}}return points.get(points.size()-1).value();}
}
