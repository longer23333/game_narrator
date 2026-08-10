package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FfmpegDeviceTest {
    @Test public void ffmpegTranscodesMovAndProResOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MobileModelBundler.ensureBundled(context);
        assertTrue("ffmpeg not ready", FfmpegRunner.isReady(context));

        File movies = new File(context.getExternalFilesDir(null), "movies");
        if (!movies.isDirectory() && !movies.mkdirs()) throw new IllegalStateException("cannot create movies dir");
        File source = new File(movies, "smoke.mp4");
        if (!source.isFile() || source.length() == 0) {
            Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
            try (InputStream in = testContext.getAssets().open("smoke.mp4");
                 OutputStream out = new FileOutputStream(source)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            }
        }
        assertTrue(source.isFile());

        File mov = new File(movies, "smoke-ffmpeg.mov");
        if (mov.exists()) mov.delete();
        FfmpegRunner.transcodeToMov(context, source, mov);
        assertTrue("MOV output missing", mov.isFile());
        assertTrue("MOV output empty", mov.length() > 0);

        File proRes = new File(movies, "smoke-prores.mov");
        if (proRes.exists()) proRes.delete();
        FfmpegRunner.transcodeToProRes(context, source, proRes);
        assertTrue("ProRes output missing", proRes.isFile());
        assertTrue("ProRes output empty", proRes.length() > 0);

        File curves = new File(movies, "smoke-curves.mp4");
        if (curves.exists()) curves.delete();
        FfmpegRunner.transcodeWithFilter(context, source, curves, "curves=preset=medium_contrast");
        assertTrue("curves output missing", curves.isFile());
        assertTrue("curves output empty", curves.length() > 0);
    }
}
