package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.AssetManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MobileModelBundlerTest {
    @Test public void bundledModelIsCopiedIntoModelsDirectory() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        File dir = MobileModelDirectory.modelsDir(target);
        File modelFile = new File(dir, "whisper-test.bin");
        File engineFile = new File(dir, "whisper-cli");
        if (modelFile.exists()) modelFile.delete();
        if (engineFile.exists()) engineFile.delete();
        int copied = MobileModelBundler.ensureBundled(assets, dir);
        assertTrue(copied >= 2);
        assertTrue(modelFile.isFile());
        assertTrue(engineFile.isFile());
        modelFile.delete();
        engineFile.delete();
    }
}
