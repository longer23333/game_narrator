package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class OnnxModelRunnerTest {
    @Test public void missingModelReportsClearErrorWithoutOverclaiming() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File empty = new File(context.getCacheDir(), "empty-onnx");
        if (!empty.isDirectory()) empty.mkdirs();
        assertNull(OnnxModelRunner.modelFile(empty, "vision"));
        assertNull(OnnxModelRunner.modelFile(empty, "text"));
        try {
            OnnxModelRunner.openChecked(empty, "vision");
            fail("expected missing-model error");
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage().contains("未检测到"));
        }
    }
}
