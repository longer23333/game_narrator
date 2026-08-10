package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import ai.onnxruntime.OrtSession;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BundledModelLoadTest {
    @Test public void bundledModelsCopyAndOpenSessionsOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileModelBundler.ensureBundled(context);
        File dir = MobileModelDirectory.modelsDir(context);
        assertTrue(new File(dir, "whisper-tiny.bin").isFile());
        assertTrue(new File(dir, "whisper-cli").isFile());
        assertTrue(new File(dir, "vision-mobilenetv2.onnx").isFile());
        assertTrue(new File(dir, "text-gpt2.onnx").isFile());
        assertTrue(new File(dir, "vocab.json").isFile());
        assertTrue(new File(dir, "merges.txt").isFile());
        assertTrue(WhisperModelRunner.isReady(context));
        OrtSession vision = OnnxModelRunner.openChecked(context, "vision");
        OnnxModelRunner.close(vision);
        OrtSession text = OnnxModelRunner.openChecked(context, "text");
        OnnxModelRunner.close(text);
    }
}
