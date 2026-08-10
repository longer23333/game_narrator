package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a project export through the same Media3 pipeline as the foreground
 * controller, outside the Activity lifecycle. Interrupted work is re-marked
 * PROCESSING when the worker is restarted; a killed process is recovered by
 * {@link MobileProjectStore}'s startup recovery.
 */
@UnstableApi
public final class ExportWorker extends Worker {
    static final String KEY_PROJECT_ID = "projectId";
    static final String KEY_JOB_ID = "jobId";
    static final String KEY_OUTPUT_PATH = "outputPath";
    static final String KEY_PRESET_LABEL = "label";
    static final String KEY_BITRATE = "bitrate";
    static final String KEY_WIDTH = "width";
    static final String KEY_HEIGHT = "height";
    static final String KEY_FRAME_RATE = "frameRate";
    static final String KEY_MIME_TYPE = "mimeType";

    private volatile HandlerThread thread;
    private volatile ExportManager manager;

    public ExportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override public Result doWork() {
        Data input = getInputData();
        long projectId = input.getLong(KEY_PROJECT_ID, -1L);
        long jobId = input.getLong(KEY_JOB_ID, -1L);
        String outputPath = input.getString(KEY_OUTPUT_PATH);
        String label = input.getString(KEY_PRESET_LABEL);
        int bitrate = input.getInt(KEY_BITRATE, 0);
        int width = input.getInt(KEY_WIDTH, 0);
        int height = input.getInt(KEY_HEIGHT, 0);
        float frameRate = input.getFloat(KEY_FRAME_RATE, 0f);
        String mimeType = input.getString(KEY_MIME_TYPE);
        if (projectId < 0 || jobId < 0 || outputPath == null || label == null || mimeType == null
                || bitrate <= 0 || width <= 0 || height <= 0 || frameRate <= 0) {
            return Result.failure();
        }
        File output = new File(outputPath);
        if (output.getParentFile() != null && !output.getParentFile().isDirectory() && !output.getParentFile().mkdirs()) {
            return Result.failure();
        }
        MobileProjectStore store = new MobileProjectStore(getApplicationContext());
        try {
            store.selectProject(projectId);
            List<TimelineClip> clips = store.loadClips();
            if (clips.isEmpty()) return Result.failure();
            store.updateExport(jobId, "PROCESSING", 0, "");
            store.updateStatus("PROCESSING");
            ExportController.Preset preset = new ExportController.Preset(label, bitrate, width, height, frameRate, mimeType);
            Composition composition = ExportController.buildProjectComposition(getApplicationContext(), store, preset, clips);
            return runExport(store, composition, preset, output, jobId);
        } catch (Exception error) {
            String message = error == null || error.getMessage() == null ? "未知导出错误" : error.getMessage();
            store.updateExport(jobId, "FAILED", 0, message);
            store.updateStatus("FAILED");
            return Result.failure();
        } finally {
            store.close();
        }
    }

    private Result runExport(MobileProjectStore store, Composition composition, ExportController.Preset preset,
                             File output, long jobId) throws Exception {
        thread = new HandlerThread("GameNarrator-export-worker");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        manager = new ExportManager(handler, new ExportManager.Listener() {
            @Override public void onStateChanged(ExportStateMachine.State state) { }
            @Override public void onProgress(int percent) {
                store.updateExport(jobId, "PROCESSING", percent, "");
            }
            @Override public void onCompleted(File file) {
                store.updateExport(jobId, "COMPLETED", 100, "");
                store.updateStatus("COMPLETED");
                success.set(true);
                latch.countDown();
            }
            @Override public void onError(String message) {
                store.updateExport(jobId, "FAILED", 0, message);
                store.updateStatus("FAILED");
                success.set(false);
                latch.countDown();
            }
            @Override public void onCancelled(File file) {
                store.updateExport(jobId, "CANCELLED", 0, "用户取消");
                success.set(false);
                latch.countDown();
            }
        });
        handler.post(() -> {
            try {
                DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(getApplicationContext())
                        .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(preset.bitrate).build())
                        .setEnableFallback(true)
                        .build();
                Transformer transformer = new Transformer.Builder(getApplicationContext())
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setVideoMimeType(preset.mimeType)
                        .setEncoderFactory(encoderFactory)
                        .build();
                manager.start(transformer, composition, output, jobId);
            } catch (Exception error) {
                String message = error == null || error.getMessage() == null ? "未知导出错误" : error.getMessage();
                store.updateExport(jobId, "FAILED", 0, message);
                store.updateStatus("FAILED");
                success.set(false);
                latch.countDown();
            }
        });
        if (!latch.await(4, TimeUnit.HOURS)) {
            handler.post(() -> {
                if (manager != null) manager.cancel();
            });
            return Result.retry();
        }
        return success.get() ? Result.success() : Result.failure();
    }

    @Override public void onStopped() {
        HandlerThread activeThread = thread;
        ExportManager activeManager = manager;
        if (activeThread != null) {
            new Handler(activeThread.getLooper()).post(() -> {
                if (activeManager != null && activeManager.isActive()) activeManager.cancel();
            });
            activeThread.quitSafely();
        }
        super.onStopped();
    }
}
