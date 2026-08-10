package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LocalVisionAnalyzerTest {
    @Test public void labelsFrameFromStressVideoOnDevice() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File folder = new File(target.getExternalFilesDir(null), "vision");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("cannot create vision dir");
        File file = new File(folder, "stress-4k.mp4");
        if (!file.isFile() || file.length() == 0) {
            Context test = InstrumentationRegistry.getInstrumentation().getContext();
            try (InputStream in = test.getAssets().open("stress-4k.mp4");
                 OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            }
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap frame = null;
        try {
            retriever.setDataSource(file.getAbsolutePath());
            frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } finally {
            retriever.release();
        }
        assertNotNull("frame must be extractable", frame);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> labels = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        LocalVisionAnalyzer.label(frame, new LocalVisionAnalyzer.Listener() {
            @Override public void onResult(List<String> result) {
                labels.set(result);
                latch.countDown();
            }
            @Override public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });
        assertTrue("vision did not finish", latch.await(90, TimeUnit.SECONDS));
        assertNull("vision failed: " + error.get(), error.get());
        assertNotNull(labels.get());
        assertTrue("expected at least one label", !labels.get().isEmpty());
    }
}
