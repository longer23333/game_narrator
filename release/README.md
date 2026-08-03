# Windows x64 发行构建

在 Windows x64、JDK 21、.NET 8 SDK 环境执行：

```powershell
.\scripts\build-windows-release.ps1 -InstallInnoSetup
```

脚本构建前后端、精简 Java 运行时、自包含桌面启动器，下载并锁定 FFmpeg，复制
Whisper、Piper、yt-dlp 及基础语音模型，最后生成 `dist/GameNarrator-Setup.exe`。

安装版使用 WinForms + Microsoft Edge WebView2 显示独立桌面窗口，不会打开默认浏览器。
构建脚本会加入微软 Evergreen WebView2 引导程序，安装时自动准备目标电脑所需运行环境。

Ollama 及视觉模型体积较大，由桌面启动器在第一次启动时从固定版本地址断点续传并进行
SHA-256 校验；这仍属于同一个图形安装向导，不要求用户另行安装 Ollama。

所有持久化数据均位于用户选择的安装目录下：程序使用 `<安装目录>\data` 保存数据库、项目、
素材、日志、配置、下载缓存、Ollama 和后续模型。启动器会自动注入路径并选择空闲端口，
用户不需要编辑 YAML 或环境变量。卸载器默认保留运行时产生的 `data`，便于重装恢复项目。
