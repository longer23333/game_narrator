package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeyframeEasingTest {
    @Test public void endpointsAreStableForAllCurves() {
        for (String easing : KeyframeEasing.values()) {
            assertEquals(0f, KeyframeEasing.apply(easing, 0f), 0.0001f);
            assertEquals(1f, KeyframeEasing.apply(easing, 1f), 0.0001f);
            assertEquals(1f, KeyframeEasing.apply(easing, 2f), 0.0001f);
            assertEquals(0f, KeyframeEasing.apply(easing, -1f), 0.0001f);
        }
    }

    @Test public void supportedAndIndexWork() {
        assertTrue(KeyframeEasing.isSupported("EASE_IN"));
        assertFalse(KeyframeEasing.isSupported("SMOOTH"));
        assertEquals(0, KeyframeEasing.index("LINEAR"));
        assertEquals(2, KeyframeEasing.index("EASE_OUT"));
        assertEquals(0, KeyframeEasing.index("UNKNOWN"));
    }
}
