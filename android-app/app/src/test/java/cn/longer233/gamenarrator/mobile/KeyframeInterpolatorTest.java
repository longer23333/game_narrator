package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class KeyframeInterpolatorTest {
    private static final class P implements KeyframeInterpolator.Point {private final long timeMs;private final float value;P(long timeMs,float value){this.timeMs=timeMs;this.value=value;}@Override public long timeMs(){return timeMs;}@Override public float value(){return value;}}
    private static final class E implements KeyframeInterpolator.Point {private final long timeMs;private final float value;private final String easing;E(long timeMs,float value,String easing){this.timeMs=timeMs;this.value=value;this.easing=easing;}@Override public long timeMs(){return timeMs;}@Override public float value(){return value;}@Override public String easing(){return easing;}}
    @Test public void interpolatesAndClampsToEndpoints(){List<P> points=List.of(new P(100,1),new P(1100,3));assertEquals(1,KeyframeInterpolator.valueAt(points,0,9),.001);assertEquals(2,KeyframeInterpolator.valueAt(points,600,9),.001);assertEquals(3,KeyframeInterpolator.valueAt(points,2000,9),.001);}
    @Test public void usesFallbackWithoutPoints(){assertEquals(.75f,KeyframeInterpolator.valueAt(List.of(),500,.75f),.001);}
    @Test public void easeInIsSlowerThanLinearAtMidpoint(){List<E> linear=List.of(new E(0,0,KeyframeEasing.LINEAR),new E(1000,1,KeyframeEasing.LINEAR));List<E> easeIn=List.of(new E(0,0,KeyframeEasing.LINEAR),new E(1000,1,KeyframeEasing.EASE_IN));float linearValue=KeyframeInterpolator.valueAt(linear,500,0);float easeInValue=KeyframeInterpolator.valueAt(easeIn,500,0);assertTrue(easeInValue<linearValue);assertEquals(1f,KeyframeInterpolator.valueAt(easeIn,1000,0),.001);}
    @Test public void easeOutIsFasterThanLinearAtMidpoint(){List<E> easeOut=List.of(new E(0,0,KeyframeEasing.LINEAR),new E(1000,1,KeyframeEasing.EASE_OUT));assertEquals(.75f,KeyframeInterpolator.valueAt(easeOut,500,0),.001);}
    @Test public void unsupportedEasingFallsBackToLinear(){List<E> unknown=List.of(new E(0,0,"NOPE"),new E(1000,2,"NOPE"));assertEquals(1f,KeyframeInterpolator.valueAt(unknown,500,0),.001);}
}
