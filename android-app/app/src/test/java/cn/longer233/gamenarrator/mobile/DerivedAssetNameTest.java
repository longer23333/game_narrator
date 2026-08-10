package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DerivedAssetNameTest {
    @Test public void sanitizesClipNames() {
        assertEquals("1", DerivedAssetName.base("场景 1"));
        assertEquals("clip", DerivedAssetName.base(""));
        assertEquals("clip", DerivedAssetName.base(null));
        assertTrue(DerivedAssetName.coverFileName("战斗 01", 123L).startsWith("01-cover-"));
        assertTrue(DerivedAssetName.coverFileName("战斗 01", 123L).endsWith(".png"));
    }
}
