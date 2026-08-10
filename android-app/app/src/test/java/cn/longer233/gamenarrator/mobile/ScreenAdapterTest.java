package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScreenAdapterTest {
    @Test public void compactWidths() {
        assertTrue(ScreenAdapter.compact(320));
        assertTrue(ScreenAdapter.compact(359));
        assertFalse(ScreenAdapter.compact(360));
        assertFalse(ScreenAdapter.compact(0));
    }

    @Test public void previewHeights() {
        assertEquals(180, ScreenAdapter.editorPreviewHeight(false, 320));
        assertEquals(150, ScreenAdapter.editorPreviewHeight(true, 320));
        assertEquals(250, ScreenAdapter.editorPreviewHeight(false, 411));
    }

    @Test public void touchTargetsStayUsable() {
        assertEquals(48, ScreenAdapter.touchTarget(42, true));
        assertEquals(48, ScreenAdapter.touchTarget(48, true));
        assertEquals(48, ScreenAdapter.touchTarget(44, false));
        assertEquals(68, ScreenAdapter.touchTarget(68, true));
    }
}
