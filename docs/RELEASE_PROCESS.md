# 统一发布流程

GameNarrator 的 Web、Windows 与 Android 使用同一个语义版本。权威版本来自根目录 `pom.xml`，Android `versionName` 必须与其完全一致；`versionCode` 按 `major * 1,000,000 + minor * 1,000 + patch` 生成并保持单调递增。Android 更新公告测试直接比较 `BuildConfig.VERSION_NAME`，不再维护另一份硬编码当前版本。

`release/CHANGELOG.json` 是当前发布说明的权威来源。Web 更新公告与 Android `MobileReleaseNotes` 的首条记录必须与其 `currentVersion`、标题一致。历史 `0.x` Android 公告仅作为迁移前记录保留，不再产生独立 Android 版本。

## 发布门禁

```powershell
.\scripts\verify-release-alignment.ps1
.\scripts\verify-android-guardrails.ps1
```

第一条命令同时校验所有版本源、统一更新日志和 `docs/ANDROID_CORE_BASELINE.json`。核心功能以 full=1、partial=0.5、missing=0 计分，Android 覆盖率不得低于 80%。每次迭代必须优先消除 partial，尤其是公共素材、平台导入、画面调整与正式签名真机兼容。

Windows 正式版与 Demo Lite 构建脚本会在修改 staging 或打包前自动调用
`scripts/verify-before-push.ps1`。统一门禁包含配置引用、release alignment、Android guardrails、
前端构建/测试和后端测试；仅当同一修订已被可信 CI 验证时，才可显式使用 `-SkipTests`。

GitHub 的“自动更新版本并发布标签”工作流只接收一个语义版本和可选 Android versionCode。工作流会在同一修订上构建 Web/后端 JAR 与 Android release APK，上传两端产物，再创建统一的中文提交和 `vX.Y.Z` 标签。

Android release 默认启用 R8 与资源裁剪，并关闭系统自动备份。发布产物除 APK 外还应归档
`android-app/app/build/outputs/mapping/release/mapping.txt`，用于还原压缩后的崩溃堆栈。
项目迁移使用应用内 `.gnproject.json` 归档，不依赖 Android Backup 恢复数据库、模型、媒体或 AI 凭据。
