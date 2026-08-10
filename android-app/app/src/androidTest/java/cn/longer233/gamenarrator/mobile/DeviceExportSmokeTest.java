package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.Transformer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@UnstableApi
public final class DeviceExportSmokeTest {
    @Test public void multiClipExportCompletesOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File movies = new File(context.getExternalFilesDir(null), "movies");
        ensureAssetVideo(context, movies, "smoke.mp4");
        File source = new File(movies, "smoke.mp4");
        assertTrue("smoke.mp4 must be prepared from assets", source.isFile());

        Uri uri = Uri.fromFile(source);
        List<EditedMediaItem> items = new ArrayList<>();
        items.add(new EditedMediaItem.Builder(MediaItem.fromUri(uri)).build());
        items.add(new EditedMediaItem.Builder(MediaItem.fromUri(uri)).build());
        EditedMediaItemSequence sequence = new EditedMediaItemSequence.Builder(items).build();
        Composition composition = new Composition.Builder(Collections.singletonList(sequence)).build();
        File output = new File(movies, "smoke-export.mp4");

        AtomicInteger progress = new AtomicInteger(-1);
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<Throwable> startError = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Handler handler = new Handler(Looper.getMainLooper());
        ExportManager manager = new ExportManager(handler, new ExportManager.Listener() {
            @Override public void onStateChanged(ExportStateMachine.State state) { }
            @Override public void onProgress(int percent) { progress.set(percent); }
            @Override public void onCompleted(File file) { done.countDown(); }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
            @Override public void onCancelled(File file) { done.countDown(); }
        });

        Transformer transformer = new Transformer.Builder(context).build();
        handler.post(() -> {
            try {
                manager.start(transformer, composition, output, 1);
            } catch (Throwable throwable) {
                startError.set(throwable);
            } finally {
                started.countDown();
            }
        });
        assertTrue("start did not finish", started.await(30, TimeUnit.SECONDS));
        assertNull("start failed: " + startError.get(), startError.get());
        assertTrue("export did not finish in time", done.await(180, TimeUnit.SECONDS));
        assertNull("export failed: " + error.get(), error.get());
        assertEquals(ExportStateMachine.State.COMPLETED, manager.state());
        assertTrue("output file missing", output.isFile());
        assertTrue("output file empty", output.length() > 0);
        assertTrue("no progress reported", progress.get() >= 0);
    }

    @Test public void cancelStopsExportOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File movies = new File(context.getExternalFilesDir(null), "movies");
        ensureAssetVideo(context, movies, "smoke.mp4");
        File source = new File(movies, "smoke.mp4");
        assertTrue("smoke.mp4 must be prepared from assets", source.isFile());

        Uri uri = Uri.fromFile(source);
        List<EditedMediaItem> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(new EditedMediaItem.Builder(MediaItem.fromUri(uri)).build());
        }
        EditedMediaItemSequence sequence = new EditedMediaItemSequence.Builder(items).build();
        Composition composition = new Composition.Builder(Collections.singletonList(sequence)).build();
        File output = new File(movies, "smoke-cancelled.mp4");

        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<Throwable> startError = new AtomicReference<>();
        CountDownLatch cancelRan = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Handler handler = new Handler(Looper.getMainLooper());
        ExportManager manager = new ExportManager(handler, new ExportManager.Listener() {
            @Override public void onStateChanged(ExportStateMachine.State state) {
                Log.i("ExportSmoke", "state=" + state);
                if (state == ExportStateMachine.State.RUNNING) running.countDown();
            }
            @Override public void onProgress(int percent) { }
            @Override public void onCompleted(File file) { Log.i("ExportSmoke", "completed"); done.countDown(); }
            @Override public void onError(String message) { Log.i("ExportSmoke", "error=" + message); error.set(message); done.countDown(); }
            @Override public void onCancelled(File file) { Log.i("ExportSmoke", "cancelled"); done.countDown(); }
        });

        Transformer transformer = new Transformer.Builder(context).build();
        handler.post(() -> {
            try {
                manager.start(transformer, composition, output, 2);
            } catch (Throwable throwable) {
                startError.set(throwable);
            } finally {
                started.countDown();
            }
        });
        assertTrue("start did not finish", started.await(30, TimeUnit.SECONDS));
        assertNull("start failed: " + startError.get(), startError.get());
        assertTrue("export did not reach RUNNING", running.await(15, TimeUnit.SECONDS));
        handler.post(() -> {
            manager.cancel();
            cancelRan.countDown();
            Log.i("ExportSmoke", "cancel called, state=" + manager.state());
        });
        assertTrue("cancel not executed", cancelRan.await(10, TimeUnit.SECONDS));
        assertTrue("cancel did not finish", done.await(20, TimeUnit.SECONDS));
        assertNull("unexpected error: " + error.get(), error.get());
        assertEquals(ExportStateMachine.State.CANCELLED, manager.state());
    }

    @Test public void subtitleTransitionAndAudioExportCompletesOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File movies = new File(context.getExternalFilesDir(null), "movies");
        ensureAssetVideo(context, movies, "smoke.mp4");
        File source = new File(movies, "smoke.mp4");
        assertTrue("smoke.mp4 must be prepared from assets", source.isFile());

        Uri uri = Uri.fromFile(source);
        List<EditedMediaItem> items = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            TimelineClip clip = new TimelineClip("k" + i, uri, "clip" + i, 0, 1000);
            clip.updateCreativeText("测试字幕", "", "[淡入淡出]");
            Effects effects = MobileRenderEffects.forClip(context, clip,
                    new ArrayList<MobileAssetStore.PlacementInfo>(),
                    new ArrayList<ProjectRepository.SubtitleCueInfo>(),
                    new ProjectRepository.ClipAudioConfig(0.8f, 200, 200),
                    new ProjectRepository.ClipVisualConfig(0, 0, 0, 0, 0, 1f, 0),
                    new ArrayList<ProjectRepository.KeyframeInfo>(),
                    new ArrayList<ProjectRepository.KeyframeInfo>(),
                    new ArrayList<ProjectRepository.KeyframeInfo>(),
                    new ArrayList<ProjectRepository.KeyframeInfo>(),
                    new ArrayList<ProjectRepository.KeyframeInfo>(),
                    new ArrayList<ProjectRepository.KeyframeInfo>(),
                    0, 720, 1280, 30f);
            items.add(new EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build());
        }
        EditedMediaItemSequence sequence = new EditedMediaItemSequence.Builder(items).build();
        Composition composition = new Composition.Builder(Collections.singletonList(sequence)).build();
        File output = new File(movies, "smoke-effects.mp4");

        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<Throwable> startError = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Handler handler = new Handler(Looper.getMainLooper());
        ExportManager manager = new ExportManager(handler, new ExportManager.Listener() {
            @Override public void onStateChanged(ExportStateMachine.State state) { }
            @Override public void onProgress(int percent) { }
            @Override public void onCompleted(File file) { done.countDown(); }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
            @Override public void onCancelled(File file) { done.countDown(); }
        });

        Transformer transformer = new Transformer.Builder(context).build();
        handler.post(() -> {
            try {
                manager.start(transformer, composition, output, 1);
            } catch (Throwable throwable) {
                startError.set(throwable);
            } finally {
                started.countDown();
            }
        });
        assertTrue("start did not finish", started.await(30, TimeUnit.SECONDS));
        assertNull("start failed: " + startError.get(), startError.get());
        assertTrue("effects export did not finish in time", done.await(180, TimeUnit.SECONDS));
        assertNull("effects export failed: " + error.get(), error.get());
        assertEquals(ExportStateMachine.State.COMPLETED, manager.state());
        assertTrue("effects output missing", output.isFile());
        assertTrue("effects output empty", output.length() > 0);
    }

    private static void ensureAssetVideo(Context target, File movies, String name) throws Exception {
        File file = new File(movies, name);
        if (file.isFile() && file.length() > 0) return;
        if (!movies.isDirectory() && !movies.mkdirs()) {
            throw new IllegalStateException("cannot create movies dir");
        }
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream in = testContext.getAssets().open(name);
             OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        }
    }
}
