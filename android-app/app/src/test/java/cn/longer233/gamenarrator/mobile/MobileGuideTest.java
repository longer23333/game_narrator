package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class MobileGuideTest {
    @Test public void guideStepsFollowDesktopWorkflowOrder() {
        List<MobileGuide.Step> steps = MobileGuide.steps();
        assertEquals(7, steps.size());
        assertTrue(steps.get(0).title().contains("完整工作流"));
        assertTrue(steps.get(1).title().contains("创建"));
        assertTrue(steps.get(6).title().contains("分享"));
        for (MobileGuide.Step step : steps) {
            assertTrue(!step.title().isBlank());
            assertTrue(!step.text().isBlank());
        }
    }
}
