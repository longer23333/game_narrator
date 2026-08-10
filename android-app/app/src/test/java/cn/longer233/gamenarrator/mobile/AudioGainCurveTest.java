package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import java.util.List;
import org.junit.Test;

public class AudioGainCurveTest {
    private static final class Point implements KeyframeInterpolator.Point {private final long timeMs;private final float value;Point(long timeMs,float value){this.timeMs=timeMs;this.value=value;}public long timeMs(){return timeMs;}public float value(){return value;}}
    @Test public void interpolatesVolumeAndCombinesFades(){List<Point> points=List.of(new Point(0,.5f),new Point(1000,1.5f));assertEquals(1f,AudioGainCurve.gainAt(points,500,1f,2000,0,0),.001f);assertEquals(0f,AudioGainCurve.gainAt(points,0,1f,2000,500,0),.001f);assertEquals(0f,AudioGainCurve.gainAt(points,2000,1f,2000,0,500),.001f);}
    @Test public void usesBaseVolumeWithoutFrames(){assertEquals(.8f,AudioGainCurve.gainAt(List.of(),500,.8f,1000,0,0),.001f);}
}
