package cn.longer233.gamenarrator.mobile;

import java.util.ArrayList;
import java.util.List;

public final class MobileGuide {
    private MobileGuide() { }

    public static final class Step {
        private final String title;
        private final String text;

        public Step(String title, String text) {
            this.title = title;
            this.text = text;
        }

        public String title() { return title; }
        public String text() { return text; }
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("先认识完整工作流",
                "从录像到成片依次经过素材读取、修剪定位、分镜文案、配音与素材放置、版本管理、时间线和视频渲染；所有处理都在手机本地完成，无需电脑或登录。"));
        steps.add(new Step("创建本地剪辑任务",
                "首页点击“建立本地剪辑任务”，从手机选择视频后进入时间线。项目、版本和素材参数保存在应用私有数据库，不会上传。"));
        steps.add(new Step("精确修剪与源监视",
                "使用播放头、逐帧按钮或 J/K/L 键标记入点和出点；预览速度只影响查看，不改变导出时长。"));
        steps.add(new Step("分镜文案与审阅",
                "逐分镜编辑字幕、解说文案和特效提示；标记“通过/需修改”并写 500 字以内备注；文案质检在手机离线打分。"));
        steps.add(new Step("端侧配音与素材",
                "选择已安装的离线 TTS 音色生成 WAV 解说；图片、视频和音频素材可放入分镜，音量、淡入淡出与 GPU 叠加真实导出。"));
        steps.add(new Step("版本、关键帧与导出",
                "历史版本可检出、重命名或建立分支；支持缩放、旋转、位置、透明度与音量关键帧；导出支持 720p/1080p/原画质、HEVC 和横竖屏画布，任务可取消或重试。"));
        steps.add(new Step("检查与分享",
                "设置页可查看诊断、AI 引擎与结构化运行日志；成片可播放、分享，项目可导出归档后在新设备恢复。"));
        return steps;
    }
}
