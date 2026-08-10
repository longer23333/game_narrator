package cn.longer233.gamenarrator.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.List;

/**
 * Effect template library dialogs: apply, save and delete.
 */
@UnstableApi
public final class TimelineEffectActions {
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);

    private final Context context;
    private final DialogController ui;
    private DialogController.TemplateHost templateHost;

    public TimelineEffectActions(Context context, DialogController ui) {
        this.context = context;
        this.ui = ui;
    }

    public void attach(DialogController.TemplateHost host) { this.templateHost = host; }

    public void showEffectTemplates() {
        if (templateHost == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("特效模板");
        panel.addView(ui.label("保存常用特效提示组合，应用到当前分镜时会与已有提示合并，不重复追加。", 13, MUTED, false));
        List<ProjectRepository.EffectTemplateInfo> templates = templateHost.listEffectTemplates();
        if (templates.isEmpty()) {
            panel.addView(ui.label("暂无模板。可先保存当前分镜特效提示。", 13, MUTED, false), ui.match(ui.dp(48)));
        }
        for (ProjectRepository.EffectTemplateInfo template : templates) {
            LinearLayout card = ui.card(Color.WHITE);
            card.addView(ui.label(template.name(), 15, TEXT, true));
            card.addView(ui.label(template.cue(), 12, MUTED, false));
            LinearLayout actions = ui.row();
            actions.addView(ui.action("应用", Color.rgb(255, 79, 163), TEXT, v -> {
                dialog.dismiss();
                applyEffectTemplate(template);
            }), new LinearLayout.LayoutParams(0, ui.dp(44), 1));
            actions.addView(ui.action("删除", Color.rgb(255, 216, 234), TEXT, v -> {
                templateHost.deleteEffectTemplate(template.id());
                dialog.dismiss();
                showEffectTemplates();
            }), new LinearLayout.LayoutParams(0, ui.dp(44), 1));
            card.addView(actions);
            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, ui.dp(7), 0, ui.dp(7));
            panel.addView(card, p);
        }
        panel.addView(ui.action("＋ 保存当前特效为模板", Color.rgb(255, 229, 72), TEXT, v -> {
            dialog.dismiss();
            saveCurrentEffectTemplate();
        }), ui.match(ui.dp(52)));
        panel.addView(ui.action("导出模板 JSON", Color.rgb(54, 201, 255), TEXT, v -> templateHost.exportTemplates()),
                ui.match(ui.dp(48)));
        panel.addView(ui.action("导入模板 JSON", Color.rgb(255, 229, 72), TEXT, v -> templateHost.importTemplates()),
                ui.match(ui.dp(48)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void applyEffectTemplate(ProjectRepository.EffectTemplateInfo template) {
        if (templateHost == null) return;
        TimelineClip clip = templateHost.currentClip();
        if (clip == null) {
            templateHost.unavailable("请先选择分镜。");
            return;
        }
        templateHost.beginEdit("应用特效模板 " + template.name());
        clip.updateCreativeText(clip.subtitle(), clip.narration(), EffectCueUtil.combine(clip.effectCue(), template.cue()));
        templateHost.commitEdit("应用特效模板 " + template.name());
        templateHost.renderTimeline();
        templateHost.logEvent("template", "应用特效模板 " + template.name());
        templateHost.setStatus("特效模板已应用到当前分镜。");
    }

    public void saveCurrentEffectTemplate() {
        if (templateHost == null) return;
        TimelineClip clip = templateHost.currentClip();
        if (clip == null) {
            templateHost.unavailable("请先选择分镜。");
            return;
        }
        if (clip.effectCue().isBlank()) {
            templateHost.unavailable("当前分镜还没有特效提示。");
            return;
        }
        EditText input = new EditText(context);
        input.setHint("模板名称（最多 40 字）");
        new AlertDialog.Builder(context).setTitle("保存特效模板")
                .setMessage("模板内容：" + clip.effectCue()).setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        templateHost.createEffectTemplate(input.getText().toString(), clip.effectCue());
                        templateHost.refreshTemplates();
                        templateHost.setStatus("特效模板已保存。");
                    } catch (Exception error) {
                        templateHost.showErrorDialog("无法保存模板", error.getMessage());
                    }
                }).show();
    }
}
