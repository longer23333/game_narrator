package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Honest capability inventory shown in the app. A capability is AVAILABLE only
 * when implemented and exercised in-app; LOCAL_ONLY when it runs entirely
 * on-device and depends on installed engines; CLOUD_REQUIRED when it needs a
 * network service; DISABLED when implemented but not yet verified; and
 * NOT_IMPLEMENTED when absent. Unverified or missing capabilities never show
 * as usable.
 */
public final class MobileCapability {
    public static final class Capability {
        private final String name;
        private final CapabilityStatus status;
        private final String detail;

        public Capability(String name, CapabilityStatus status, String detail) {
            this.name = name;
            this.status = status;
            this.detail = detail == null ? "" : detail;
        }

        public String name() { return name; }
        public CapabilityStatus status() { return status; }
        public String detail() { return detail; }
    }

    private MobileCapability() { }

    public static List<Capability> current() {
        return current(new MobileModelDirectory.Presence(false, false, false), false);
    }

    public static List<Capability> current(MobileModelDirectory.Presence models) {
        return current(models, false);
    }

    public static List<Capability> current(MobileModelDirectory.Presence models, boolean speechAvailable) {
        return current(models, speechAvailable, false);
    }

    public static List<Capability> current(MobileModelDirectory.Presence models, boolean speechAvailable,
                                           boolean whisperEngineReady) {
        boolean whisper = models != null && models.whisper();
        boolean whisperUsable = whisper && whisperEngineReady;
        boolean vision = (models != null && models.vision()) || LocalVisionAnalyzer.isAvailable();
        boolean text = models != null && models.text();
        List<Capability> out = new ArrayList<>();
        out.add(new Capability("本地媒体导入", CapabilityStatus.AVAILABLE,
                "通过系统文件选择器读取并保留持久 URI 权限，媒体不离开设备。"));
        out.add(new Capability("多片段时间线编辑", CapabilityStatus.AVAILABLE,
                "新增、删除、移动、分割、替换与 50 步内撤回/恢复。"));
        out.add(new Capability("AI 配音（系统 TTS）", CapabilityStatus.LOCAL_ONLY,
                "使用已安装的离线中文 TTS 引擎生成解说；缺少语音包时设置页会明确标记不可用。"));
        out.add(new Capability("平台导入（HTTP/HTTPS 直链）", CapabilityStatus.LOCAL_ONLY,
                "支持媒体直链与网页直链解析下载；已加载的 Cookie 会话会自动带上，需要登录的平台页面可配合 Bilibili 登录助手使用。"));
        out.add(new Capability("字幕烧录", CapabilityStatus.LOCAL_ONLY,
                "分镜字幕与精确字幕轨已接入 Media3 导出，真机验证通过。"));
        out.add(new Capability("转场", CapabilityStatus.LOCAL_ONLY,
                "淡入淡出与交叉转场已接入 Media3 合成，真机验证通过。"));
        out.add(new Capability("音频混合", CapabilityStatus.LOCAL_ONLY,
                "原声/解说/音乐轨道、音量与淡入淡出已接入导出，真机验证通过。"));
        out.add(new Capability("AI 视觉（画面理解）", vision ? CapabilityStatus.LOCAL_ONLY : CapabilityStatus.NOT_IMPLEMENTED,
                vision ? "ML Kit 端侧图像标签已可用，可识别画面标签；分镜语义理解仍需 vision-*.onnx 模型。"
                        : "未检测到 vision-*.onnx，暂不提供分镜画面理解。"));
        out.add(new Capability("AI 字幕（自动转写/生成）",
                whisperUsable ? CapabilityStatus.LOCAL_ONLY : CapabilityStatus.NOT_IMPLEMENTED,
                whisper && !whisperEngineReady
                        ? "已检测到 whisper-*.bin，但模型目录缺少 whisper-cli 引擎，暂不标记可用。"
                        : whisper ? "检测到端侧转写模型与 whisper-cli 引擎，可生成自动字幕。"
                        : "未检测到 whisper-*.bin，尚未提供自动字幕转写引擎。"));
        out.add(new Capability("端侧转写", speechAvailable ? CapabilityStatus.LOCAL_ONLY : CapabilityStatus.NOT_IMPLEMENTED,
                speechAvailable ? "系统语音识别可用，听写结果仅内存显示，不持久化。"
                        : "系统未提供语音识别服务。"));
        out.add(new Capability("端侧 AI 剧情/分镜生成", text ? CapabilityStatus.LOCAL_ONLY : CapabilityStatus.NOT_IMPLEMENTED,
                text ? "检测到端侧文案模型，可生成剧情与分镜文案。"
                        : "未检测到 text-*.onnx，需要安装可运行的端侧模型后才可标记为可用。"));
        return out;
    }
}
