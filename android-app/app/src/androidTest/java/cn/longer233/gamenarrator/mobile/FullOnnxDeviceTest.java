package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertTrue;

import ai.onnxruntime.OrtSession;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FullOnnxDeviceTest {
    @Test public void fullVisionAndTextModelsOpenOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileModelBundler.ensureBundled(context);
        File models = MobileModelDirectory.modelsDir(context);
        assertTrue("vision ONNX model missing", new File(models, "vision-mobilenetv2.onnx").length() > 10_000_000);
        assertTrue("text ONNX model missing", new File(models, "text-gpt2.onnx").length() > 100_000_000);
        OrtSession vision = OnnxModelRunner.openChecked(context, "vision");
        OnnxModelRunner.close(vision);
        OrtSession text = OnnxModelRunner.openChecked(context, "text");
        OnnxModelRunner.close(text);
    }
}
