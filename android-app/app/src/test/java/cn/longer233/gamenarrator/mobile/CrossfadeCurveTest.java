package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CrossfadeCurveTest {
    @Test public void rampsFromZeroToOneAcrossOverlap() {
        assertEquals(0f, CrossfadeCurve.alpha(0, 1000, 1000), 0.0001f);
        assertEquals(0.5f, CrossfadeCurve.alpha(1500, 1000, 1000), 0.0001f);
        assertEquals(1f, CrossfadeCurve.alpha(2000, 1000, 1000), 0.0001f);
        assertEquals(1f, CrossfadeCurve.alpha(3000, 1000, 1000), 0.0001f);
    }
}
