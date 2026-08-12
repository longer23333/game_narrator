# Windows x64 发行构建

在 Windows x64、JDK 21、.NET 8 SDK 环境执行：

```powershell
.\scripts\build-windows-release.ps1 -InstallInnoSetup
```

发布脚本默认先运行统一门禁：配置引用、release 版本一致性、Android guardrails、前端构建与测试、
后端测试。只有在已经由同一修订的可信 CI 完成验证时，才可显式传入 `-SkipTests`；跳过测试不再是默认行为。

构建会先执行 `npm ci`，因此删除或移动 `frontend/node_modules` 后仍可重复构建。Whisper、
Piper、yt-dlp 和模型默认可放在项目的忽略目录中；也可通过环境变量指向项目外依赖目录：

```powershell
$env:GAME_NARRATOR_BUILD_DEPS_ROOT = 'G:\shiping\game_narrator-local-20260803'
.\scripts\build-windows-release.ps1 -SkipDownloads
```

Demo Lite 使用同一策略：

```powershell
.\scripts\build-windows-demo-lite.ps1
# 仅在外部已完成同一修订验证时：
.\scripts\build-windows-demo-lite.ps1 -SkipTests
```

每次提交前建议执行：

```powershell
.\scripts\verify-before-push.ps1
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
