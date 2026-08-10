package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ImageSearchDeviceTest {
    @Test public void mobilenetEmbeddingsRetrieveMatchingShotOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileModelBundler.ensureBundled(context);

        Bitmap warm = solid(224, 224, Color.rgb(220, 60, 60));
        Bitmap cool = solid(224, 224, Color.rgb(40, 90, 210));

        float[] warmVector = MobileNetVisionClassifier.embed(context, warm);
        float[] coolVector = MobileNetVisionClassifier.embed(context, cool);
        assertNotNull(warmVector);
        assertNotNull(coolVector);
        assertTrue(warmVector.length > 100);
        assertEquals(warmVector.length, coolVector.length);

        ImageSearchIndex index = new ImageSearchIndex();
        index.add("warm-shot", warmVector);
        index.add("cool-shot", coolVector);
        assertEquals(2, index.size());

        List<String> warmHit = index.search(warmVector, 1);
        List<String> coolHit = index.search(coolVector, 1);
        assertEquals("warm-shot", warmHit.get(0));
        assertEquals("cool-shot", coolHit.get(0));
    }

    private static Bitmap solid(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
}
