package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import android.content.Context;
import androidx.media3.common.Effect;
import androidx.media3.effect.SingleColorLut;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CubeLutDeviceTest {
    @Test public void previewAndExportUseSingleColorLutFromSameFactory() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File lut = new File(context.getCacheDir(), "identity.cube");
        Files.write(lut.toPath(), ("LUT_3D_SIZE 2\n"
                + "0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n").getBytes(StandardCharsets.UTF_8));
        ProjectRepository.ClipVisualConfig config = new ProjectRepository.ClipVisualConfig(0, 0, 0, 0, 0, 1, 0, lut.getAbsolutePath());
        List<Effect> preview = MobileRenderEffects.previewVideoEffects(config);
        assertEquals(1, preview.size());
        assertTrue(preview.get(0) instanceof SingleColorLut);
        assertTrue(CubeLutEffectFactory.fromPath(config.lutPath()) instanceof SingleColorLut);
    }
}
