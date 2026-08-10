package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class ImageSearchIndexTest {
    @Test public void nearestVectorWins() {
        ImageSearchIndex index = new ImageSearchIndex();
        index.add("cat", new float[]{1f, 0.2f, 0f});
        index.add("dog", new float[]{0.9f, 0.1f, 0.05f});
        index.add("sky", new float[]{0f, 0f, 1f});
        List<String> result = index.search(new float[]{1f, 0.1f, 0f}, 2);
        assertEquals("dog", result.get(0));
        assertEquals("cat", result.get(1));
    }
}
