package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public final class StoryboardDialogController {
    private static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);
    private static final int DANGER = Color.rgb(255, 85, 119);

    private final Context context;
    private final DialogController ui;
    private DialogController.StoryboardHost storyboardHost;
    private DialogController.EditorHost editorHost;

    public StoryboardDialogController(Context context, DialogController ui) {
        this.context = context;
        this.ui = ui;
    }

    public void attachStoryboardHost(DialogController.StoryboardHost host) {
        this.storyboardHost = host;
    }

    public void attachEditorHost(DialogController.EditorHost host) {
        this.editorHost = host;
    }

    public void openSubtitlePanel() {
        if (storyboardHost == null) return;
        TimelineClip clip = storyboardHost.currentClip();
        if (clip == null) {
            storyboardHost.unavailable("请先选择片段。");
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("分镜文案");
        panel.addView(ui.label("分别编辑画面字幕、解说文案和特效提示。", 13, MUTED, false));
        EditText subtitle = ui.creativeInput("字幕", clip.subtitle());
        EditText narration = ui.creativeInput("解说文案", clip.narration());
        EditText effectCue = ui.creativeInput("特效提示", clip.effectCue());
        CheckBox dynamicSubtitle = new CheckBox(context);
        dynamicSubtitle.setText("逐字动态字幕");
        dynamicSubtitle.setTextColor(TEXT);
        dynamicSubtitle.setChecked(clip.effectCue().contains("[动态字幕]"));
        CheckBox fadeTransition = new CheckBox(context);
        fadeTransition.setText("片段边界淡入淡出");
        fadeTransition.setTextColor(TEXT);
        fadeTransition.setChecked(clip.effectCue().contains("[淡入淡出]"));
        CheckBox crossfade = new CheckBox(context);
        crossfade.setText("与下一片段交叉转场");
        crossfade.setTextColor(TEXT);
        crossfade.setChecked(clip.effectCue().contains("[交叉转场]"));
        panel.addView(subtitle, ui.match(ui.dp(76)));
        panel.addView(narration, ui.match(ui.dp(76)));
        panel.addView(effectCue, ui.match(ui.dp(76)));
        panel.addView(dynamicSubtitle, ui.match(ui.dp(44)));
        panel.addView(fadeTransition, ui.match(ui.dp(44)));
        panel.addView(crossfade, ui.match(ui.dp(44)));
        panel.addView(ui.action("保存分镜文案", Color.rgb(255, 229, 72), TEXT, v -> {
            storyboardHost.beginEdit("修改分镜字幕、解说和特效提示");
            String cue = effectCue.getText().toString()
                    .replace("[动态字幕]", "").replace("[淡入淡出]", "").replace("[交叉转场]", "").trim();
            if (dynamicSubtitle.isChecked()) cue = (cue + " [动态字幕]").trim();
            if (fadeTransition.isChecked()) cue = (cue + " [淡入淡出]").trim();
            if (crossfade.isChecked()) cue = (cue + " [交叉转场]").trim();
            clip.updateCreativeText(subtitle.getText().toString(), narration.getText().toString(), cue);
            storyboardHost.commitEdit("修改分镜字幕、解说和特效提示");
            storyboardHost.renderTimeline();
            dialog.dismiss();
            storyboardHost.setStatus("分镜文案已保存到对应轨道。");
        }), ui.match(ui.dp(54)));
        dialog.setContentView(panel);
        dialog.show();
    }

    public void reviewCurrentClip() {
        if (storyboardHost == null) return;
        TimelineClip clip = storyboardHost.currentClip();
        if (clip == null) {
            storyboardHost.unavailable("请先选择片段。");
            return;
        }
        ProjectRepository.ClipReviewInfo review = storyboardHost.clipReview(clip.key());
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("分镜审阅");
        String state = "APPROVED".equals(review.status()) ? "已通过"
                : "NEEDS_CHANGES".equals(review.status()) ? "需修改" : "待审阅";
        panel.addView(ui.label(clip.name() + " · " + state, 17, TEXT, true));
        panel.addView(ui.label("与本体一致：评审只记录人工结论，不覆盖 AI 质量结果。备注可说明事实冲突、节奏或措辞问题。", 13, MUTED, false));
        EditText note = ui.creativeInput("评审备注（最多 500 字）", review.note());
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        panel.addView(note, ui.match(ui.dp(92)));
        LinearLayout actions = ui.row();
        actions.addView(ui.action("通过", Color.rgb(113, 230, 108), TEXT, v -> {
            storyboardHost.reviewClip(clip.key(), "APPROVED", note.getText().toString());
            dialog.dismiss();
            storyboardHost.renderTimeline();
            storyboardHost.setStatus("当前分镜已标记为通过。");
        }), new LinearLayout.LayoutParams(0, ui.dp(54), 1));
        actions.addView(ui.action("需修改", DANGER, TEXT, v -> {
            storyboardHost.reviewClip(clip.key(), "NEEDS_CHANGES", note.getText().toString());
            dialog.dismiss();
            storyboardHost.renderTimeline();
            storyboardHost.setStatus("当前分镜已标记为需修改。");
        }), new LinearLayout.LayoutParams(0, ui.dp(54), 1));
        panel.addView(actions);
        dialog.setContentView(panel);
        dialog.show();
    }

    public void showScriptQuality() {
        if (storyboardHost == null) return;
        List<TimelineClip> clips = storyboardHost.allClips();
        List<MobileScriptQualityAnalyzer.Segment> segments = new ArrayList<>();
        for (TimelineClip clip : clips) {
            segments.add(new MobileScriptQualityAnalyzer.Segment(clip.key(), clip.durationMs(),
                    clip.subtitle(), clip.narration(), clip.effectCue()));
        }
        MobileScriptQualityAnalyzer.Result result = MobileScriptQualityAnalyzer.analyze(segments);
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("文案质量评审");
        int qualityColor = result.passed() ? Color.rgb(113, 230, 108) : DANGER;
        panel.addView(ui.label("文案质量 " + result.score() + " / 100", 24, qualityColor, true));
        panel.addView(ui.label(result.summary() + "。检查空字段、重复文本、明显乱码和镜头可配音时长；全部在手机离线完成。", 13, MUTED, false));
        if (result.issues().isEmpty()) {
            panel.addView(ui.label("没有发现规则性问题。事实一致性仍应由人工或端侧视觉模型复核。", 14, TEXT, false));
        } else {
            for (MobileScriptQualityAnalyzer.Issue issue : result.issues()) {
                int index = -1;
                for (int i = 0; i < clips.size(); i++) {
                    if (clips.get(i).key().equals(issue.clipKey())) { index = i; break; }
                }
                int target = index;
                String prefix = index >= 0 ? "分镜 " + (index + 1) + " · " : "";
                panel.addView(ui.action(prefix + issue.message(), Color.WHITE, TEXT, v -> {
                    if (target >= 0) {
                        storyboardHost.selectClip(target);
                        dialog.dismiss();
                        storyboardHost.openStoryboardPanel();
                    }
                }), ui.match(ui.dp(52)));
            }
        }
        TextView visualResult = ui.label("画面一致性检查尚未运行。", 13, MUTED, false);
        panel.addView(ui.action("端侧画面一致性检查（MobileNet）", Color.rgb(54, 201, 255), TEXT, v -> {
            if (!MobileModelDirectory.check(context).vision()) {
                visualResult.setText("未检测到端侧视觉模型，无法执行画面一致性检查。");
                return;
            }
            visualResult.setText("正在提取各分镜画面并离线分类…（手机端较慢）");
            final List<TimelineClip> targets = new ArrayList<>(clips);
            new Thread(() -> {
                try {
                    List<String> allLabels = new ArrayList<>();
                    List<MobileScriptQualityAnalyzer.Issue> visualIssues = new ArrayList<>();
                    for (TimelineClip clip : targets) {
                        Bitmap frame = null;
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(context, clip.uri());
                            long at = clip.startMs() + Math.max(0, clip.durationMs() / 2);
                            frame = retriever.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        } catch (Exception ignored) {
                            frame = null;
                        } finally {
                            try { retriever.release(); } catch (Exception ignored) { }
                        }
                        if (frame == null) continue;
                        List<String> labels = MobileNetVisionClassifier.classify(context, frame);
                        if (!allLabels.isEmpty()) allLabels.add(" / ");
                        allLabels.addAll(labels);
                        visualIssues.addAll(MobileScriptQualityAnalyzer.visualConsistency(clip.key(), clip.narration(), labels));
                    }
                    final String summary = allLabels.isEmpty() ? "没有提取到可识别的画面。"
                            : "画面标签：" + String.join("", allLabels);
                    final List<MobileScriptQualityAnalyzer.Issue> issues = visualIssues;
                    activityHandlerPost(() -> {
                        visualResult.setText(summary);
                        if (!issues.isEmpty()) {
                            for (MobileScriptQualityAnalyzer.Issue issue : issues) {
                                panel.addView(ui.label("需复核 路 " + issue.message(), 13, DANGER, false));
                            }
                        }
                    });
                } catch (Exception error) {
                    final String message = error.getMessage() == null ? "未知错误" : error.getMessage();
                    activityHandlerPost(() -> visualResult.setText("画面一致性检查失败：" + message));
                }
            }).start();
        }), ui.match(ui.dp(52)));
        panel.addView(visualResult);
        dialog.setContentView(panel);
        dialog.show();
    }

    private void activityHandlerPost(Runnable action) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(action);
    }

    public void showEditor() {
        if (editorHost == null) return;
        ViewGroup container = editorHost.pageContainer();
        if (container == null) return;
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(12));
        LinearLayout top = ui.row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.iconButton("‹", v -> editorHost.showHomePage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        LinearLayout title = ui.column();
        title.addView(ui.label(editorHost.activeProjectName(), 18, TEXT, true));
        TextView projectSummary = ui.label("本地项目 · 0 个片段", 12, MUTED, false);
        title.addView(projectSummary);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(ui.action("版本", Color.rgb(255, 229, 72), TEXT,
                v -> editorHost.openRevisionPanel()), new LinearLayout.LayoutParams(ui.dp(62), ui.dp(48)));
        top.addView(ui.action("记录", Color.rgb(54, 201, 255), TEXT,
                v -> editorHost.showExportJobs()), new LinearLayout.LayoutParams(ui.dp(62), ui.dp(48)));
        top.addView(ui.action("阶段", SURFACE_HIGH, TEXT,
                v -> editorHost.showPipelinePanel()), new LinearLayout.LayoutParams(ui.dp(62), ui.dp(48)));
        top.addView(ui.action("导出", Color.rgb(255, 79, 163), TEXT,
                v -> editorHost.startProjectExport()), new LinearLayout.LayoutParams(ui.dp(76), ui.dp(48)));
        root.addView(top);
        FrameLayout previewFrame = new FrameLayout(context);
        previewFrame.setBackgroundColor(Color.BLACK);
        PlayerView preview = new PlayerView(context);
        preview.setPlayer(editorHost.player());
        previewFrame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        TextView localBadge = ui.label("● 本地预览", 11, Color.rgb(255, 79, 163), true);
        localBadge.setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5));
        localBadge.setTextColor(TEXT);
        localBadge.setBackground(ui.outlined(Color.rgb(113, 230, 108), 0));
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        badge.setMargins(0, ui.dp(10), ui.dp(10), 0);
        previewFrame.addView(localBadge, badge);
        LinearLayout.LayoutParams previewParams = ui.match(ui.dp(ScreenAdapter.editorPreviewHeight(
                editorHost.landscape(), editorHost.screenWidthDp())));
        previewParams.setMargins(0, ui.dp(8), 0, ui.dp(12));
        root.addView(previewFrame, previewParams);
        HorizontalScrollView monitorScroll = new HorizontalScrollView(context);
        monitorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout monitor = ui.row();
        monitor.addView(ui.action("◀ 帧", Color.WHITE, TEXT, v -> editorHost.stepFrame(-1)),
                new LinearLayout.LayoutParams(ui.dp(68), ui.dp(48)));
        monitor.addView(ui.action("播放/暂停", Color.rgb(255, 229, 72), TEXT, v -> editorHost.playOrPause()),
                new LinearLayout.LayoutParams(ui.dp(96), ui.dp(48)));
        monitor.addView(ui.action("帧 ▶", Color.WHITE, TEXT, v -> editorHost.stepFrame(1)),
                new LinearLayout.LayoutParams(ui.dp(68), ui.dp(48)));
        monitor.addView(ui.action("[ 入点", Color.rgb(54, 201, 255), TEXT, v -> editorHost.markBoundary(true)),
                new LinearLayout.LayoutParams(ui.dp(72), ui.dp(48)));
        monitor.addView(ui.action("出点 ]", Color.rgb(255, 79, 163), TEXT, v -> editorHost.markBoundary(false)),
                new LinearLayout.LayoutParams(ui.dp(72), ui.dp(48)));
        monitor.addView(ui.action("速度", SURFACE_HIGH, TEXT, v -> editorHost.showPlaybackSpeed()),
                new LinearLayout.LayoutParams(ui.dp(66), ui.dp(48)));
        monitorScroll.addView(monitor);
        root.addView(monitorScroll, ui.match(ui.dp(52)));
        LinearLayout timelineHeader = ui.row();
        timelineHeader.setGravity(Gravity.CENTER_VERTICAL);
        timelineHeader.addView(ui.sectionTitle("自由剪辑时间线"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        timelineHeader.addView(ui.action("＋ 素材", Color.rgb(255, 229, 72), TEXT,
                v -> editorHost.launchVideoPicker()));
        root.addView(timelineHeader);
        TextView ruler = ui.label("00:00        00:05        00:10        00:15", 11, MUTED, false);
        ruler.setTypeface(Typeface.MONOSPACE);
        root.addView(ruler);
        LinearLayout zoomRow = ui.row();
        zoomRow.setGravity(Gravity.CENTER_VERTICAL);
        zoomRow.addView(ui.label("缩放", 12, TEXT, true));
        SeekBar zoom = new SeekBar(context);
        zoom.setMin(24);
        zoom.setMax(120);
        zoom.setProgress(editorHost.timelineZoom());
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) {
                    editorHost.setTimelineZoom(value);
                    editorHost.renderTimeline();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        zoomRow.addView(zoom, new LinearLayout.LayoutParams(0, ui.dp(38), 1));
        root.addView(zoomRow);
        FrameLayout trackFrame = new FrameLayout(context);
        HorizontalScrollView trackScroll = new HorizontalScrollView(context);
        trackScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout timeline = ui.row();
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        timeline.setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4));
        timeline.setOnDragListener((view, event) -> editorHost.handleTimelineDrag(event, timeline));
        trackScroll.addView(timeline);
        trackFrame.addView(trackScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        View playhead = new View(context);
        playhead.setBackgroundColor(Color.rgb(255, 79, 163));
        FrameLayout.LayoutParams line = new FrameLayout.LayoutParams(ui.dp(2), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL);
        trackFrame.addView(playhead, line);
        TextView emptyTimeline = ui.label("导入视频后，缩略图时间线会显示在这里", 14, MUTED, false);
        emptyTimeline.setGravity(Gravity.CENTER);
        trackFrame.addView(emptyTimeline, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(trackFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(104)));
        WaveformView waveformView = new WaveformView(context);
        LinearLayout.LayoutParams waveformParams = ui.match(ui.dp(52));
        waveformParams.setMargins(0, ui.dp(4), 0, ui.dp(4));
        root.addView(waveformView, waveformParams);
        LinearLayout subtitleTrack = addTrackLane(root, "字幕", Color.rgb(255, 79, 163));
        LinearLayout narrationTrack = addTrackLane(root, "解说", Color.rgb(255, 229, 72));
        LinearLayout effectTrack = addTrackLane(root, "特效", Color.rgb(54, 201, 255));
        LinearLayout assetTrack = addTrackLane(root, "素材", Color.rgb(113, 230, 108));
        TextView status = ui.label("所有媒体处理均在手机本地完成。", 12, MUTED, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(36)));
        HorizontalScrollView toolsScroll = new HorizontalScrollView(context);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = ui.row();
        tools.setPadding(0, ui.dp(4), 0, 0);
        tools.addView(ui.tool("↶", "撤回", v -> editorHost.undo()));
        tools.addView(ui.tool("↷", "恢复", v -> editorHost.redo()));
        tools.addView(ui.tool("✂", "刀片分割", v -> editorHost.split()));
        tools.addView(ui.tool("∞", "连接右侧", v -> editorHost.mergeRight()));
        tools.addView(ui.tool("轨", "轨道分配", v -> editorHost.showTrackAssignment()));
        tools.addView(ui.tool("⌫", "删除片段", v -> editorHost.confirmDelete()));
        tools.addView(ui.tool("↔", "起止时间", v -> editorHost.openClipPanel()));
        tools.addView(ui.tool("◐", "画面调整", v -> editorHost.showClipVisualAdjustments()));
        tools.addView(ui.tool("◆", "关键帧", v -> editorHost.showKeyframes()));
        tools.addView(ui.tool("↗", "位置关键帧", v -> editorHost.showPositionKeyframes()));
        tools.addView(ui.tool("α", "透明度关键帧", v -> editorHost.showOpacityKeyframes()));
        tools.addView(ui.tool("音", "音量关键帧", v -> editorHost.showVolumeKeyframes()));
        tools.addView(ui.tool("∿", "关键帧曲线", v -> editorHost.showKeyframeCurveEditor()));
        tools.addView(ui.tool("▦", "特效模板", v -> editorHost.showEffectTemplates()));
        tools.addView(ui.tool("T", "分镜文案", v -> editorHost.openSubtitlePanel()));
        tools.addView(ui.tool("✓", "分镜审阅", v -> editorHost.reviewCurrentClip()));
        tools.addView(ui.tool("质", "文案质检", v -> editorHost.showScriptQuality()));
        tools.addView(ui.tool("CC", "字幕文件", v -> editorHost.showSubtitleFiles()));
        tools.addView(ui.tool("◇", "素材放置", v -> editorHost.openAssetPlacement()));
        tools.addView(ui.tool("＋", "加入合集", v -> editorHost.addCurrentClipToCompilation()));
        tools.addView(ui.tool("声", "生成配音", v -> editorHost.synthesizeNarration()));
        tools.addView(ui.tool("M/S", "音轨控制", v -> editorHost.showTrackControls()));
        toolsScroll.addView(tools);
        root.addView(toolsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(72)));
        container.removeAllViews();
        container.addView(root);
        editorHost.attachEditorViews(timeline, ruler, projectSummary, emptyTimeline, waveformView,
                subtitleTrack, narrationTrack, effectTrack, assetTrack, status);
        editorHost.renderTimeline();
        editorHost.selectCurrentIfValid();
    }

    private LinearLayout addTrackLane(LinearLayout root, String name, int color) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.label(name, 11, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setBackground(ui.outlined(color, 0));
        row.addView(title, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(30)));
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout lane = ui.row();
        lane.setPadding(ui.dp(2), 0, ui.dp(2), 0);
        scroll.addView(lane);
        row.addView(scroll, new LinearLayout.LayoutParams(0, ui.dp(30), 1));
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(32)));
        return lane;
    }
}
