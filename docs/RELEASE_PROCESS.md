# 统一发布流程

GameNarrator 的 Web、Windows 与 Android 使用同一个语义版本。权威版本来自根目录 `pom.xml`，Android `versionName` 必须与其完全一致；`versionCode` 按 `major * 1,000,000 + minor * 1,000 + patch` 生成并保持单调递增。Android 更新公告测试直接比较 `BuildConfig.VERSION_NAME`，不再维护另一份硬编码当前版本。

`release/CHANGELOG.json` 是当前发布说明的权威来源。Web 更新公告与 Android `MobileReleaseNotes` 的首条记录必须与其 `currentVersion`、标题一致。历史 `0.x` Android 公告仅作为迁移前记录保留，不再产生独立 Android 版本。

## 发布门禁

```powershell
.\scripts\verify-release-alignment.ps1
.\scripts\verify-android-guardrails.ps1
```

第一条命令同时校验所有版本源、统一更新日志和 `docs/ANDROID_CORE_BASELINE.json`。核心能力状态以 full=1、partial=0.5、missing=0 计分，加权指标不得低于 80%；它不是代码覆盖率。baseline schema v2 要求每项非 missing 能力提供结构化文档表格行、生产源码路径和 `测试类#测试方法`，门禁会验证文件与 `@Test` 方法真实存在。partial 的测试只证明已实现部分，不能替代剩余真机或功能验收。每次迭代必须优先消除 partial，尤其是公共素材、平台导入、画面调整与正式签名真机兼容。

Windows 正式版与 Demo Lite 构建脚本会在修改 staging 或打包前自动调用
`scripts/verify-before-push.ps1`。统一门禁包含配置引用、release alignment、Android guardrails、
前端构建/测试和后端测试；仅当同一修订已被可信 CI 验证时，才可显式使用 `-SkipTests`。

GitHub 的“自动更新版本并发布标签”工作流只接收一个语义版本和可选 Android versionCode。工作流会在同一修订上构建 Web/后端 JAR 与 Android release APK，上传两端产物，再创建统一的中文提交和 `vX.Y.Z` 标签。

Android release 默认启用 R8 与资源裁剪，并关闭系统自动备份。发布产物除 APK 外还应归档
`android-app/app/build/outputs/mapping/release/mapping.txt`，用于还原压缩后的崩溃堆栈。
项目迁移使用应用内 `.gnproject.json` 归档，不依赖 Android Backup 恢复数据库、模型、媒体或 AI 凭据。

正式 Android 构建必须通过环境变量提供 `GN_KEYSTORE`、`GN_KEYSTORE_PASSWORD`、`GN_KEY_ALIAS`、
`GN_KEY_PASSWORD`。`build-android-release-signed.ps1` 默认拒绝缺少生产密钥的构建；`-AllowTestKey`
只能用于本地非发布审计。脚本会同时归档 APK、R8 mapping/usage/resources、证书信息、SHA-256 与
包含提交哈希的制品清单。`verify-android-production-release.ps1 -RequireProductionArtifacts
-RequireUpgradeEvidence` 只有在生产签名产物及 2.2.4→当前版本真机升级证据同时存在时才通过。平台导入和升级报告采用 schema v2，必须位于 `artifacts`、匹配当前提交与版本、在 7 天内完成，并包含唯一会话和非占位设备信息；模板填满布尔值但提交、版本、设备或时间不匹配仍会失败。

常规 CI 的 `release-evidence` 汇总作业仅在 migration、documentation、frontend、Android、Android emulator、backend、真实 PostgreSQL integration 和 performance baseline 全部成功后运行，生成 `ci-release-result-<commit>` 制品。下载其中的 `artifacts/ci-release-result.json` 后，`verify-ci-release-evidence.ps1` 会强制校验当前提交、八项门禁、schema v2、运行链接和 7 天有效期；发布就绪度页面还会校验相同语义，空文件、旧 schema 或旧提交不能显示绿色。
