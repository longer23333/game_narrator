package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EffectCueUtilTest {
    @Test public void validatesTemplateNames() {
        assertTrue(EffectCueUtil.isValidName("电影感开场"));
        assertFalse(EffectCueUtil.isValidName(""));
        assertFalse(EffectCueUtil.isValidName("   "));
        assertFalse(EffectCueUtil.isValidName("x".repeat(41)));
    }

    @Test public void combineKeepsExistingAndAvoidsDuplicates() {
        assertEquals("高对比", EffectCueUtil.combine("", "高对比"));
        assertEquals("高对比 / 暖色", EffectCueUtil.combine("高对比", "暖色"));
        assertEquals("高对比", EffectCueUtil.combine("高对比", "高对比"));
        assertEquals("高对比 / 暖色", EffectCueUtil.combine("高对比 / 暖色", "暖色"));
    }

    @Test public void detectsCrossfadeCue() {
        assertTrue(EffectCueUtil.hasCrossfade("[交叉转场]"));
        assertTrue(EffectCueUtil.hasCrossfade("高对比 交叉转场"));
        assertFalse(EffectCueUtil.hasCrossfade("淡入淡出"));
        assertFalse(EffectCueUtil.hasCrossfade(null));
    }
}
