package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class TextEmbeddingDeviceTest {
    @Test public void embedsChineseSemanticsOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileModelBundler.ensureBundled(context);
        assertTrue("embedding model not ready", TextEmbedding.isReady(context));

        float[] gameA = TextEmbedding.encode(context, "游戏画面非常精彩");
        float[] gameB = TextEmbedding.encode(context, "游戏画面非常精彩");
        float[] market = TextEmbedding.encode(context, "股票市场分析报告");
        assertTrue(gameA.length == 768);
        assertTrue(gameB.length == 768);
        assertTrue(market.length == 768);

        float same = cosine(gameA, gameB);
        float different = cosine(gameA, market);
        assertTrue("same text should score highest, got " + same + " vs " + different, same > different);
    }

    private static float cosine(float[] a, float[] b) {
        float dot = 0;
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) dot += a[i] * b[i];
        return dot;
    }
}
