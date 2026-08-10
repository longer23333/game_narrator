package cn.longer233.gamenarrator.mobile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.List;

@UnstableApi
public final class DiagnosticsDialogController {
    private static final int SURFACE_HIGH = Color.rgb(255, 241, 166);
    private static final int TEXT = Color.rgb(17, 17, 17);
    private static final int MUTED = Color.rgb(70, 80, 92);
    private static final int DANGER = Color.rgb(255, 85, 119);

    private final Context context;
    private final DialogController ui;
    private final DialogController.SettingsHost settingsHost;
    private DialogController.SettingsPageHost settingsPageHost;

    public DiagnosticsDialogController(Context context, DialogController.SettingsHost settingsHost,
                                       DialogController ui) {
        this.context = context;
        this.settingsHost = settingsHost;
        this.ui = ui;
    }

    public void attachSettingsPageHost(DialogController.SettingsPageHost host) {
        this.settingsPageHost = host;
    }

    public void showInstalledVoices(List<String> voiceLabels) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("已安装离线音色");
        panel.addView(ui.label("只列出手机已安装且可离线使用的 TTS 音色。", 13, MUTED, false));
        if (voiceLabels == null || voiceLabels.isEmpty()) {
            panel.addView(ui.label("没有检测到离线音色，请在系统文字转语音设置中下载语音包。", 14, MUTED, false), ui.match(ui.dp(52)));
        }
        if (voiceLabels != null) {
            for (String voiceLabel : voiceLabels) {
                panel.addView(ui.label(voiceLabel, 13, TEXT, false), ui.match(ui.dp(42)));
            }
        }
        panel.addView(ui.action("关闭", Color.rgb(255, 79, 163), TEXT, v -> dialog.dismiss()), ui.match(ui.dp(50)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showGuide(String versionName) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("GameNarrator Android 使用引导");
        panel.addView(ui.label("当前版本 v" + versionName
                + " · 与桌面本体的操作顺序一致：导入 → 修剪 → 分镜 → 配音/素材 → 版本 → 导出。", 13, MUTED, false));
        List<MobileGuide.Step> steps = MobileGuide.steps();
        for (int i = 0; i < steps.size(); i++) {
            MobileGuide.Step step = steps.get(i);
            LinearLayout card = ui.card(i % 2 == 0 ? SURFACE_HIGH : Color.WHITE);
            card.addView(ui.label("第 " + (i + 1) + " 步 · " + step.title(), 15, TEXT, true));
            card.addView(ui.label(step.text(), 13, MUTED, false));
            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, ui.dp(6), 0, ui.dp(6));
            panel.addView(card, p);
        }
        panel.addView(ui.action("开始使用", Color.rgb(255, 79, 163), TEXT, v -> dialog.dismiss()), ui.match(ui.dp(54)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showReleaseNotes(String versionName) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("更新公告");
        panel.addView(ui.label("GameNarrator Android v" + versionName, 18, TEXT, true));
        for (MobileReleaseNotes.Note note : MobileReleaseNotes.notes()) {
            LinearLayout card = ui.card(Color.WHITE);
            card.addView(ui.label(note.version() + " · " + note.title(), 15, TEXT, true));
            card.addView(ui.label(note.body(), 13, MUTED, false));
            LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, ui.dp(6), 0, ui.dp(6));
            panel.addView(card, p);
        }
        panel.addView(ui.action("关闭", Color.rgb(255, 79, 163), TEXT, v -> dialog.dismiss()), ui.match(ui.dp(52)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    public void showAiSettings() {
        if (settingsHost == null) return;
        ViewGroup container = settingsHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28));
        LinearLayout head = ui.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ui.iconButton("‹", v -> settingsHost.showHomePage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        head.addView(ui.label("AI 设置 · 用量 · 模型检查", 22, TEXT, true), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        root.addView(head);
        LinearLayout note = ui.card(SURFACE_HIGH);
        note.addView(ui.label("只展示已安装可运行引擎", 16, TEXT, true));
        note.addView(ui.label("未安装的转写、视觉理解和文案模型会明确标注，不会被显示为可用。", 13, MUTED, false));
        root.addView(note);
        MobileModelDirectory.Presence presence = MobileModelDirectory.check(context);
        boolean modelsReady = presence.whisper() && presence.vision() && presence.text();
        LinearLayout installCard = ui.card(Color.WHITE);
        if (modelsReady) {
            installCard.addView(ui.label("模型状态：已自动安装", 16, TEXT, true));
            installCard.addView(ui.label("Whisper、ONNX 视觉与文案模型已从 APK 复制到本地模型目录。", 12, MUTED, false));
        } else {
            installCard.addView(ui.label("模型自动安装", 16, TEXT, true));
            TextView installStatus = ui.label("检测到模型未就绪，可点击按钮从 APK 复制。", 12, MUTED, false);
            installCard.addView(ui.action("立即安装/刷新模型", Color.rgb(54, 201, 255), TEXT, v -> {
                installStatus.setText("正在复制模型，约需几十秒…");
                new Thread(() -> {
                    try {
                        MobileModelBundler.ensureBundled(context);
                        new Handler(Looper.getMainLooper()).post(() -> showAiSettings());
                    } catch (Exception error) {
                        new Handler(Looper.getMainLooper()).post(() -> installStatus.setText("复制失败："
                                + (error.getMessage() == null ? "未知错误" : error.getMessage())));
                    }
                }).start();
            }), ui.match(ui.dp(50)));
            installCard.addView(installStatus);
        }
        root.addView(installCard);
        String report = settingsHost.engineReport();
        TextView reportView = ui.label(report, 12, TEXT, false);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        reportView.setBackground(ui.outlined(Color.WHITE, 0));
        root.addView(reportView, ui.match(ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout capabilityCard = ui.card(Color.WHITE);
        capabilityCard.addView(ui.label("能力状态", 18, TEXT, true));
        capabilityCard.addView(ui.label("只把已实现并有验证的能力标记为可用；未实现或需要模型的能力不会显示为已完成。", 13, MUTED, false));
        MobileModelDirectory.Presence models = MobileModelDirectory.check(context);
        for (MobileCapability.Capability capability : MobileCapability.current(models,
                LocalTranscriber.isAvailable(context), WhisperModelRunner.isReady(context))) {
            capabilityCard.addView(ui.label("· " + capability.name() + "：" + capability.status().label(), 14, TEXT, true));
            if (!capability.detail().isBlank()) {
                capabilityCard.addView(ui.label(capability.detail(), 12, MUTED, false));
            }
        }
        root.addView(capabilityCard);
        LinearLayout modelCard = ui.card(Color.WHITE);
        modelCard.addView(ui.label("端侧模型目录", 16, TEXT, true));
        modelCard.addView(ui.label(MobileModelDirectory.modelsDir(context).getAbsolutePath(), 11, MUTED, false));
        modelCard.addView(ui.label("把 whisper-*.bin / vision-*.onnx / text-*.onnx 放入该目录，并把 whisper-cli（Android 可执行文件）一并放入后，对应能力才会标记为本地可用。", 12, MUTED, false));
        modelCard.addView(ui.label(WhisperModelRunner.isReady(context)
                ? "Whisper.cpp：模型与 whisper-cli 引擎均已就绪。"
                : "Whisper.cpp：等待 whisper-*.bin 与 whisper-cli 引擎。", 12, MUTED, false));
        root.addView(modelCard);
        LinearLayout dictationCard = ui.card(Color.WHITE);
        dictationCard.addView(ui.label("系统离线转写", 16, TEXT, true));
        dictationCard.addView(ui.label(LocalTranscriber.isAvailable(context)
                ? "可用（麦克风听写测试）" : "不可用（系统未提供语音识别）", 13, TEXT, true));
        TextView transcript = ui.label("识别结果只显示在这里，不会上传或保存。", 12, MUTED, false);
        dictationCard.addView(ui.action("开始听写测试", Color.rgb(54, 201, 255), TEXT, v -> {
            if (!LocalTranscriber.isAvailable(context)) {
                transcript.setText("系统语音识别不可用。");
                return;
            }
            transcript.setText("正在听写…请开始说话。");
            settingsHost.requestMicPermission(() -> {
                try {
                    LocalTranscriber.start(context, new LocalTranscriber.Listener() {
                        @Override public void onPartial(String text) {
                            new Handler(Looper.getMainLooper()).post(() -> transcript.setText("…" + text));
                        }
                        @Override public void onResult(String text) {
                            new Handler(Looper.getMainLooper()).post(() -> transcript.setText("识别结果：" + text));
                        }
                        @Override public void onError(String message) {
                            new Handler(Looper.getMainLooper()).post(() -> transcript.setText("识别失败：" + message));
                        }
                    });
                } catch (Exception error) {
                    transcript.setText("识别失败：" + (error.getMessage() == null ? "未知错误" : error.getMessage()));
                }
            });
        }), ui.match(ui.dp(50)));
        dictationCard.addView(transcript);
        root.addView(dictationCard);
        LinearLayout whisperCard = ui.card(Color.WHITE);
        whisperCard.addView(ui.label("Whisper.cpp 转写", 16, TEXT, true));
        whisperCard.addView(ui.label(WhisperModelRunner.isReady(context)
                ? "模型与 whisper-cli 引擎已就绪，可选择 WAV 测试转写。"
                : "等待 whisper-*.bin 与 whisper-cli 引擎。", 13, MUTED, false));
        TextView whisperResult = ui.label("转写结果只显示在这里，不自动入库。", 12, MUTED, false);
        whisperCard.addView(ui.action("选择 WAV 转写", Color.rgb(54, 201, 255), TEXT, v -> {
            if (!WhisperModelRunner.isReady(context)) {
                whisperResult.setText("模型或 whisper-cli 引擎未就绪。");
                return;
            }
            whisperResult.setText("正在选择音频…");
            settingsHost.launchWhisperWavOpen(text ->
                    new Handler(Looper.getMainLooper()).post(() -> whisperResult.setText(text)));
        }), ui.match(ui.dp(50)));
        whisperCard.addView(whisperResult);
        root.addView(whisperCard);
        LinearLayout gpt2Card = ui.card(Color.WHITE);
        gpt2Card.addView(ui.label("GPT-2 文案生成（ONNX）", 16, TEXT, true));
        EditText prompt = ui.creativeInput("输入提示词（英文效果更好）", "In a quiet village,");
        TextView gptResult = ui.label("生成结果只显示在这里，不自动入库。", 12, MUTED, false);
        gpt2Card.addView(prompt);
        gpt2Card.addView(ui.action("生成本地文案", Color.rgb(54, 201, 255), TEXT, v -> {
            String text = prompt.getText().toString().trim();
            if (text.isBlank()) {
                gptResult.setText("请输入提示词。");
                return;
            }
            gptResult.setText("正在本地生成…（手机端较慢）");
            new Thread(() -> {
                try {
                    String output = Gpt2OnnxGenerator.generate(context, text);
                    new Handler(Looper.getMainLooper()).post(() -> gptResult.setText("生成结果：\n" + output));
                } catch (Exception error) {
                    new Handler(Looper.getMainLooper()).post(() -> gptResult.setText("生成失败："
                            + (error.getMessage() == null ? "未知错误" : error.getMessage())));
                }
            }).start();
        }), ui.match(ui.dp(50)));
        gpt2Card.addView(gptResult);
        root.addView(gpt2Card);
        LinearLayout visionCard = ui.card(Color.WHITE);
        visionCard.addView(ui.label("MobileNetV2 画面分类（ONNX）", 16, TEXT, true));
        TextView visionResult = ui.label("分类结果只显示在这里，不自动入库。", 12, MUTED, false);
        visionCard.addView(ui.action("选择图片分类", Color.rgb(54, 201, 255), TEXT, v -> {
            visionResult.setText("正在选择图片…");
            settingsHost.launchVisionImageOpen(text ->
                    new Handler(Looper.getMainLooper()).post(() -> visionResult.setText(text)));
        }), ui.match(ui.dp(50)));
        visionCard.addView(visionResult);
        root.addView(visionCard);
        LinearLayout cloudCard = ui.card(Color.WHITE);
        cloudCard.addView(ui.label("云端 AI 设置", 16, TEXT, true));
        cloudCard.addView(ui.label("API Key 使用系统加密存储，绝不写入数据库、日志或归档。", 12, MUTED, false));
        AiCloudSettings cloud = new AiCloudSettings(context);
        String[] providers = {"DASHSCOPE", "DEEPSEEK", "OPENAI", "ANTHROPIC", "GEMINI", "OPENROUTER",
                "SILICONFLOW", "MOONSHOT", "ZHIPU", "VOLCENGINE", "BAIDU", "TENCENT", "MINIMAX", "XAI",
                "MISTRAL", "GROQ", "TOGETHER", "PERPLEXITY", "CEREBRAS", "OPENAI_COMPATIBLE"};
        Spinner providerSpinner = new Spinner(context);
        providerSpinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, providers));
        int providerIndex = 0;
        for (int i = 0; i < providers.length; i++) {
            if (providers[i].equals(cloud.provider())) providerIndex = i;
        }
        providerSpinner.setSelection(providerIndex);
        EditText baseUrl = ui.creativeInput("接口地址", cloud.baseUrl());
        EditText visionModel = ui.creativeInput("视觉模型", cloud.visionModel());
        EditText textModel = ui.creativeInput("文案模型", cloud.textModel());
        EditText keyInput = ui.creativeInput("API Key（留空表示保留已保存值）", "");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView cloudStatus = ui.label(cloud.hasApiKey() ? "已保存 API Key（隐藏）" : "未保存 API Key", 12, MUTED, false);
        cloudCard.addView(ui.label("服务商", 13, MUTED, true));
        cloudCard.addView(providerSpinner, ui.match(ui.dp(48)));
        cloudCard.addView(baseUrl);
        cloudCard.addView(visionModel);
        cloudCard.addView(textModel);
        cloudCard.addView(keyInput, ui.match(ui.dp(64)));
        cloudCard.addView(cloudStatus);
        cloudCard.addView(ui.action("加密保存设置", Color.rgb(54, 201, 255), TEXT, v -> {
            String key = keyInput.getText().toString().trim();
            cloud.save(providers[providerSpinner.getSelectedItemPosition()], baseUrl.getText().toString().trim(),
                    visionModel.getText().toString().trim(), textModel.getText().toString().trim(),
                    key.isBlank() ? cloud.apiKey() : key);
            cloudStatus.setText("已加密保存。");
            keyInput.setText("");
        }), ui.match(ui.dp(50)));
        cloudCard.addView(ui.action("测试连接（真实请求云端）", Color.rgb(113, 230, 108), TEXT, v -> {
            String key = keyInput.getText().toString().trim();
            cloud.save(providers[providerSpinner.getSelectedItemPosition()], baseUrl.getText().toString().trim(),
                    visionModel.getText().toString().trim(), textModel.getText().toString().trim(),
                    key.isBlank() ? cloud.apiKey() : key);
            cloudStatus.setText("正在请求 " + providers[providerSpinner.getSelectedItemPosition()] + "…");
            new Thread(() -> {
                try {
                    String result = CloudAiClient.test(cloud);
                    new Handler(Looper.getMainLooper()).post(() -> cloudStatus.setText(result));
                } catch (Exception error) {
                    String message = error.getMessage() == null ? "未知错误" : error.getMessage();
                    new Handler(Looper.getMainLooper()).post(() -> cloudStatus.setText("连接失败：" + message));
                }
            }).start();
        }), ui.match(ui.dp(50)));
        cloudCard.addView(ui.action("清除 API Key", DANGER, TEXT, v -> {
            cloud.clearApiKey();
            cloudStatus.setText("已清除 API Key。");
        }), ui.match(ui.dp(46)));
        root.addView(cloudCard);
        LinearLayout actions = ui.row();
        actions.addView(ui.action("查看离线音色", Color.rgb(54, 201, 255), TEXT,
                v -> settingsHost.showInstalledVoicesPage()), new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        actions.addView(ui.action("使用引导", Color.rgb(255, 229, 72), TEXT,
                v -> settingsHost.showGuidePage()), new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        actions.addView(ui.action("更新公告", Color.rgb(255, 79, 163), TEXT,
                v -> settingsHost.showReleaseNotesPage()), new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        root.addView(actions, ui.match(ui.dp(56)));
        root.addView(ui.action("未来版本规划", Color.rgb(54, 201, 255), TEXT, v -> showRoadmap()),
                ui.match(ui.dp(48)));
        LinearLayout logCard = ui.card(Color.WHITE);
        logCard.addView(ui.label("结构化运行日志", 18, TEXT, true));
        logCard.addView(ui.label("应用关键事件以 JSONL 追加写入应用私有目录，最多保留 2000 条 / 1 MiB。", 13, MUTED, false));
        logCard.addView(ui.label("当前 " + settingsHost.logCount() + " 条 · 最近事件：", 13, TEXT, true));
        String tail = settingsHost.logTail(3);
        logCard.addView(ui.label(tail.isBlank() ? "暂无日志。" : tail, 11, MUTED, false));
        LinearLayout logActions = ui.row();
        logActions.addView(ui.action("导出日志", Color.rgb(54, 201, 255), TEXT,
                v -> settingsHost.exportStructuredLog()), new LinearLayout.LayoutParams(0, ui.dp(50), 1));
        logActions.addView(ui.action("清空日志", DANGER, TEXT,
                v -> settingsHost.confirmClearStructuredLog()), new LinearLayout.LayoutParams(0, ui.dp(50), 1));
        logCard.addView(logActions);
        root.addView(logCard);
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }

    public void showRoadmap() {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout panel = ui.sheet("未来版本规划");
        addRoadmap(panel, "1.0", "自动剪辑与特效生成",
                "完成正常的视频剪辑、常见特效创作与成片导出，覆盖字幕、转场、贴图、音效和画面包装。");
        addRoadmap(panel, "2.0", "AI 剧情与分镜工作台",
                "支持手动修改 AI 剧情和剪辑决策，提供完整分镜时间线页面，便于调整镜头、字幕、配音和特效。");
        addRoadmap(panel, "3.0", "AI 动画剧场与 Meme",
                "使用 AI 绘图创作角色、场景和动画剧场画面，并生成符合网络传播语境的 Meme 与视频包装素材。");
        addRoadmap(panel, "4.0", "多创作者风格一键生成",
                "建立多个 UP 主类型的创作风格模板，组合节奏、文案、配音、字幕和特效，实现一键生成。");
        addRoadmap(panel, "5.0", "AI 创意助手",
                "面向游戏、动漫和生活片段生成多套脚本方案、画面需求与素材清单，并在合法授权范围内自动检索所需素材。");
        panel.addView(ui.label("Android 独立版按上述路径推进；端侧模型与授权素材接口未就绪时会明确标注。", 12, MUTED, false));
        panel.addView(ui.action("关闭", Color.rgb(255, 79, 163), TEXT, v -> dialog.dismiss()), ui.match(ui.dp(52)));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private void addRoadmap(LinearLayout panel, String version, String title, String body) {
        LinearLayout card = ui.card(Color.WHITE);
        card.addView(ui.label(version + "  " + title, 15, TEXT, true));
        card.addView(ui.label(body, 13, MUTED, false));
        LinearLayout.LayoutParams p = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, ui.dp(6), 0, ui.dp(6));
        panel.addView(card, p);
    }

    public void showSettings() {
        if (settingsPageHost == null) return;
        ViewGroup container = settingsPageHost.pageContainer();
        if (container == null) return;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = ui.column();
        root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(28));
        LinearLayout head = ui.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(ui.iconButton("‹", v -> settingsPageHost.showHomePage()), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        head.addView(ui.label("设置与诊断", 24, TEXT, true), new LinearLayout.LayoutParams(0, ui.dp(48), 1));
        root.addView(head);
        LinearLayout aiEntry = ui.card(SURFACE_HIGH);
        aiEntry.addView(ui.action("打开 AI 设置 · 用量 · 模型检查", Color.rgb(54, 201, 255), TEXT,
                v -> settingsHost.showAiSettingsPage()), ui.match(ui.dp(52)));
        root.addView(aiEntry);
        LinearLayout privacy = ui.card(SURFACE_HIGH);
        privacy.addView(ui.label("LOCAL ONLY", 12, TEXT, true));
        privacy.addView(ui.label("诊断报告不会包含账号、Cookie、访问令牌或媒体内容。", 14, TEXT, false));
        root.addView(privacy);
        String report = MobileDiagnostics.inspect(context, settingsPageHost.ttsReady());
        TextView diagnostics = ui.label(report, 12, TEXT, false);
        diagnostics.setTextIsSelectable(true);
        diagnostics.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        diagnostics.setBackground(ui.outlined(Color.WHITE, 0));
        LinearLayout.LayoutParams reportParams = ui.match(ViewGroup.LayoutParams.WRAP_CONTENT);
        reportParams.setMargins(0, ui.dp(12), 0, ui.dp(10));
        root.addView(diagnostics, reportParams);
        LinearLayout actions = ui.row();
        actions.addView(ui.action("复制诊断", Color.rgb(54, 201, 255), TEXT, v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("GameNarrator 诊断", report));
            }
        }), new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        actions.addView(ui.action("导出/分享", Color.rgb(255, 79, 163), TEXT,
                v -> settingsPageHost.shareDiagnostics(report)), new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        root.addView(actions);
        LinearLayout storage = ui.card(Color.WHITE);
        storage.addView(ui.label("存储维护", 18, TEXT, true));
        storage.addView(ui.label("只清理应用私有缓存，不删除项目数据库、源媒体引用或已导出视频。", 13, MUTED, false));
        storage.addView(ui.action("安全清理缓存", Color.rgb(255, 229, 72), TEXT,
                v -> ui.confirm("清理应用缓存？", "缩略图和临时诊断文件会删除，可在需要时重新生成。", "清理",
                        settingsPageHost::clearCacheThenRefresh)), ui.match(ui.dp(52)));
        root.addView(storage);
        LinearLayout archive = ui.card(Color.WHITE);
        archive.addView(ui.label("项目归档与恢复", 18, TEXT, true));
        archive.addView(ui.label("归档包含时间线、分镜文案、版本快照和素材参数；媒体大文件不会重复打包，恢复时仍需原文件 URI 可访问。导入始终创建新项目，不覆盖现有工程。", 13, MUTED, false));
        LinearLayout archiveActions = ui.row();
        archiveActions.addView(ui.action("导出当前项目", Color.rgb(54, 201, 255), TEXT,
                v -> settingsPageHost.beginProjectArchive()), new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        archiveActions.addView(ui.action("导入恢复", Color.rgb(255, 79, 163), TEXT,
                v -> settingsPageHost.launchProjectArchiveOpen()), new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        archive.addView(archiveActions);
        root.addView(archive);
        LinearLayout recycle = ui.card(Color.WHITE);
        recycle.addView(ui.label("项目回收站", 18, TEXT, true));
        List<ProjectRepository.ProjectInfo> archivedProjects = settingsPageHost.archivedProjects();
        if (archivedProjects.isEmpty()) {
            recycle.addView(ui.label("回收站为空。", 13, MUTED, false));
        }
        for (ProjectRepository.ProjectInfo value : archivedProjects) {
            LinearLayout line = ui.row();
            line.addView(ui.label(value.name() + " · " + value.clipCount() + " 个片段", 13, TEXT, true),
                    new LinearLayout.LayoutParams(0, ui.dp(46), 1));
            line.addView(ui.action("恢复", Color.rgb(113, 230, 108), TEXT,
                    v -> settingsPageHost.restoreProject(value.id())), new LinearLayout.LayoutParams(ui.dp(64), ui.dp(44)));
            line.addView(ui.action("永久删除", DANGER, TEXT,
                    v -> settingsPageHost.permanentlyDeleteProject(value)), new LinearLayout.LayoutParams(ui.dp(84), ui.dp(44)));
            recycle.addView(line, ui.match(ui.dp(48)));
        }
        root.addView(recycle);
        LinearLayout engines = ui.card(Color.WHITE);
        engines.addView(ui.label("端侧引擎", 18, TEXT, true));
        MobileModelDirectory.Presence presence = MobileModelDirectory.check(context);
        String transcribe = WhisperModelRunner.isReady(context) ? "已安装"
                : (presence.whisper() ? "模型已检测到，引擎待装" : "未安装");
        String vision = presence.vision() ? "已安装" : "未安装";
        String text = presence.text() ? "已安装" : "未安装";
        engines.addView(ui.label("转写：" + transcribe + "\n视觉理解：" + vision + "\n文案模型：" + text
                + "\n语音合成：" + (settingsPageHost.ttsReady() ? "系统中文 TTS 可用" : "缺少中文 TTS 语音包"),
                13, MUTED, false));
        root.addView(engines);
        scroll.addView(root);
        container.removeAllViews();
        container.addView(scroll);
    }
}
