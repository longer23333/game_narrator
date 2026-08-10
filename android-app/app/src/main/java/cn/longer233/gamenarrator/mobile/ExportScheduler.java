package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import androidx.media3.common.util.UnstableApi;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.io.File;

/**
 * Enqueues project exports as WorkManager jobs so rendering survives the
 * Activity lifecycle, with battery and storage constraints.
 */
@UnstableApi
public final class ExportScheduler {
    private ExportScheduler() { }

    public static String uniqueName(long projectId) {
        return "project-export-" + projectId;
    }

    public static void enqueue(Context context, long projectId, long jobId, File output, ExportController.Preset preset) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();
        Data data = new Data.Builder()
                .putLong(ExportWorker.KEY_PROJECT_ID, projectId)
                .putLong(ExportWorker.KEY_JOB_ID, jobId)
                .putString(ExportWorker.KEY_OUTPUT_PATH, output.getAbsolutePath())
                .putString(ExportWorker.KEY_PRESET_LABEL, preset.label)
                .putInt(ExportWorker.KEY_BITRATE, preset.bitrate)
                .putInt(ExportWorker.KEY_WIDTH, preset.width)
                .putInt(ExportWorker.KEY_HEIGHT, preset.height)
                .putFloat(ExportWorker.KEY_FRAME_RATE, preset.frameRate)
                .putString(ExportWorker.KEY_MIME_TYPE, preset.mimeType)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ExportWorker.class)
                .setConstraints(constraints)
                .setInputData(data)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(projectId),
                ExistingWorkPolicy.REPLACE, request);
    }
}
