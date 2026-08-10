package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PipelineStagesTest {
    @Test public void orderAndLabelsAreStable() {
        String[] order = PipelineStages.order();
        assertEquals(6, order.length);
        assertEquals("IMPORT", order[0]);
        assertEquals("RENDER", order[5]);
        assertEquals("素材导入", PipelineStages.label("IMPORT"));
        assertEquals("视频渲染", PipelineStages.label("RENDER"));
        assertEquals("UNKNOWN", PipelineStages.label("UNKNOWN"));
    }

    @Test public void nextAndCompletionWork() {
        assertEquals("TRIM", PipelineStages.next("IMPORT"));
        assertNull(PipelineStages.next("RENDER"));
        assertTrue(PipelineStages.isCompleted("IMPORT", "TRIM"));
        assertTrue(PipelineStages.isCompleted("STORYBOARD", "REVIEW"));
        assertFalse(PipelineStages.isCompleted("ASSETS", "ASSETS"));
        assertFalse(PipelineStages.isCompleted("ASSETS", "TRIM"));
    }
}
