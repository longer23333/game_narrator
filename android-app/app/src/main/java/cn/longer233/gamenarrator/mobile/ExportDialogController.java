package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.util.List;
import java.util.Locale;

@UnstableApi
public final class ExportDialogController {
    private static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);
    private static final int GREEN = Color.rgb(113, 230, 108);
    private static final int DANGER = Color.rgb(255, 85, 119);

    private final Context context;
    private final DialogController.ExportHost exportHost;
    private final DialogController ui;
    private BottomSheetDialog exportProgressDialog;
    private TextView exportProgressText;
    private ProgressBar exportProgressBar;

    public ExportDialogController(Context context, DialogController.ExportHost exportHost, DialogController ui) {
        this.context = context;
        this.exportHost = exportHost;
        this.ui = ui;
    }

    public void showExportJobs() {
        if (exportHost == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout content = ui.sheet("导出任务");
        List<MobileExportStore.ExportJobInfo> jobs = exportHost.exportJobs();
        if (jobs.isEmpty()) content.addView(ui.label("暂无导出记录。", 14, MUTED, false));
        for (MobileExportStore.ExportJobInfo job : jobs) {
            String time = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new java.util.Date(job.createdAt()));
            String detail = time + " · " + exportHost.exportStatusLabel(job.status())
                    + ("PROCESSING".equals(job.status()) ? " " + job.progress() + "%" : "");
            if (!job.preset().isBlank()) detail += "\n" + job.preset();
            if (!job.error().isBlank()) detail += "\n" + job.error();
            Button item = ui.action(detail, "COMPLETED".equals(job.status()) ? GREEN :
                    "FAILED".equals(job.status()) ? Color.rgb(255, 216, 234) : SURFACE_HIGH, TEXT, v -> {
                if ("COMPLETED".equals(job.status())) {
                    File file = new File(job.outputPath());
                    if (file.isFile()) exportHost.showExportFile(file);
                    else exportHost.showErrorDialog("文件不存在", "导出记录存在，但文件已被移动或删除。");
                } else if ("PROCESSING".equals(job.status()) && job.id() == exportHost.activeExportJobId()) {
                    exportHost.cancelActiveExport();
                    dialog.dismiss();
                } else {
                    dialog.dismiss();
                    exportHost.startProjectExport();
                }
            });
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams p = ui.match(ui.dp(job.error().isBlank() ? 58 : 78));
            p.setMargins(0, ui.dp(8), 0, 0);
            content.addView(item, p);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showExportProgress(String dialogTitle, String label) {
        dismissExportProgress();
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet(dialogTitle);
        exportProgressText = ui.label("准备本地渲染 · " + label, 14, MUTED, false);
        exportProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        exportProgressBar.setMax(100);
        exportProgressBar.setIndeterminate(true);
        panel.addView(exportProgressText);
        panel.addView(exportProgressBar, ui.match(ui.dp(36)));
        panel.addView(ui.label("视频不会上传。", 12, MUTED, false));
        panel.addView(ui.action("取消导出", DANGER, TEXT, v -> {
            if (exportHost != null) exportHost.cancelActiveExport();
            dialog.dismiss();
        }), ui.match(ui.dp(52)));
        dialog.setCancelable(false);
        dialog.setContentView(panel);
        dialog.show();
        exportProgressDialog = dialog;
    }

    public void updateExportProgress(int percent) {
        if (exportProgressBar != null) {
            exportProgressBar.setIndeterminate(false);
            exportProgressBar.setProgress(percent);
        }
        if (exportProgressText != null) exportProgressText.setText("本地渲染 " + percent + "%");
    }

    public void dismissExportProgress() {
        if (exportProgressDialog != null && exportProgressDialog.isShowing()) exportProgressDialog.dismiss();
        exportProgressDialog = null;
        exportProgressText = null;
        exportProgressBar = null;
    }

    public void showCustomExport() {
        if (exportHost == null) return;
        SharedPreferences settings = context.getSharedPreferences("export", Context.MODE_PRIVATE);
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), 0);
        EditText height = ui.number("输出高度，0 表示原分辨率", settings.getInt("height", 1080));
        EditText fps = ui.number("帧率，0 表示跟随源", settings.getInt("fps", 30));
        EditText bitrate = ui.number("视频码率 Mbps", settings.getInt("bitrate_mbps", 8));
        panel.addView(height);
        panel.addView(fps);
        panel.addView(bitrate);
        new AlertDialog.Builder(context).setTitle("自定义导出")
                .setMessage("高度保持宽高比；帧率转换只丢弃多余帧，不虚构新帧。")
                .setView(panel).setNegativeButton("取消", null).setPositiveButton("开始导出", (dialog, which) -> {
                    int h = (int) longValue(height);
                    int f = (int) longValue(fps);
                    int mbps = (int) longValue(bitrate);
                    if ((h != 0 && (h < 240 || h > 2160)) || (f != 0 && (f < 12 || f > 120)) || mbps < 1 || mbps > 100) {
                        exportHost.showErrorDialog("导出参数无效",
                                "高度应为 0 或 240–2160，帧率应为 0 或 12–120，码率应为 1–100 Mbps。");
                        return;
                    }
                    settings.edit().putInt("height", h).putInt("fps", f).putInt("bitrate_mbps", mbps).apply();
                    String label = "自定义 · " + (h == 0 ? "原分辨率" : h + "p") + " / "
                            + (f == 0 ? "原帧率" : f + " FPS") + " / " + mbps + " Mbps";
                    exportHost.startExport(new ExportController.Preset(label, mbps * 1_000_000, 0, h, f, MimeTypes.VIDEO_H264));
                }).show();
    }

    public void showExportActions(File file) {
        new AlertDialog.Builder(context).setTitle("最终成片").setMessage(file.getAbsolutePath())
                .setNeutralButton("播放", (dialog, which) -> openExportFile(file))
                .setPositiveButton("分享", (dialog, which) -> shareExportFile(file))
                .setNegativeButton("关闭", null).show();
    }

    public Uri exportUri(File file) {
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".files", file);
    }

    public void openExportFile(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(exportUri(file), "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (Exception error) {
            ui.showError("无法播放", error.getMessage());
        }
    }

    public void shareExportFile(File file) {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, exportUri(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "分享 GameNarrator 成片"));
    }

    private static long longValue(EditText field) {
        try {
            return Long.parseLong(field.getText().toString());
        } catch (Exception ignored) {
            return 0;
        }
    }
}
