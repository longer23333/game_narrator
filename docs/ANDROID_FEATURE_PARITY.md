# GameNarrator Android 功能同步矩阵

本文件以当前桌面本体源码、控制器、前端和迁移为验收基线。Android 必须完全独立运行；“入口存在”不等于功能完成。

核心功能基线由 `docs/ANDROID_CORE_BASELINE.json` 机器校验，当前能力状态加权指标为 94.4%（16 项 full、2 项 partial），不得低于 80%。该数值不是代码覆盖率：每项非 missing 能力还必须给出真实文档表格行、生产源码文件和 `测试类#测试方法`，verifier 会检查文件与 `@Test` 方法确实存在；partial 的测试只证明已实现部分，不能把未完成项算作 full。Web、Windows 与 Android 从 2.2.4 起共享同一语义版本；历史 Android `0.x` 版本只保留在更新记录中。

| 本体能力 | Android 状态 | 验收要求 |
|---|---|---|
| 本体新粗野主义视觉、品牌与导航 | 已完成（模拟器截图） | 视觉、品牌与导航已按桌面本体实现；模拟器首页截图见 docs/android-home-screen.png，逐页对照与正式真机验收仍建议在真机上完成 |
| 源监视器与精确修剪 | 已完成 | 原片播放、片段定位、按真实帧率逐帧、触屏与 J/K/L、播放速度、入点/出点标记及历史持久化均已实现 |
| 创建任务、运行中、最近完成 | 已完成 | 创建任务表单（名称/内容类别/解说风格/剪辑范围/目标时长/创作要求/术语纠错词表/分镜后暂停/自动剪辑流程）已持久化并在首页任务卡展示；多项目、重命名、复制、软删除回收站、恢复、永久清理、状态恢复、导出取消/重试，以及一键自动流水线已实现：Whisper 离线转写缺失字幕 → GPT-2 生成缺失解说文案 → 离线 TTS 批量合成配音 → 自动导出；勾选“启动自动剪辑流程”后导入视频会自动启动该流水线，勾选“分镜后暂停”时在文案生成后等待检查；端侧模型未安装时明确提示并保留可完成部分 |
| 自由时间线、撤回/恢复、分割、连接、删除 | 已完成 | 缩放、拖拽、播放头和 50 步历史，片段左右边缘可直接拖拽调整入点/出点，以及 V1 主视频轨、V2 视频叠层轨、A1 纯音频轨多轨时间线（轨道分配、导出按轨合成、归档/分支保留轨道）均已实现 |
| 项目版本树与历史检出 | 已完成 | 100 个 SQLite 修订快照、检出、版本命名、从任意快照建立独立分支、项目归档导出、事务化恢复、版本差异对比与分支合并（只补缺失/覆盖同名）均已实现 |
| 分镜、字幕、解说、审阅 | 已完成 | 字幕/解说/特效提示编辑、项目级 SRT、按全局时间码真实烧录，以及与本体一致的逐分镜“通过/需修改”和 500 字备注已完成 |
| 文案质量评审 | 已完成 | 手机离线输出 0–100 分并检查空字段、重复、明显乱码、镜头可配音时长；端侧画面一致性检查已接入（MobileNet 提取分镜画面标签，与解说文案做本地启发式比对并标注需人工复核） |
| 分镜重新配音 | 已完成 | 读取设备实际安装的离线 TTS 音色，逐分镜保存音色、0.5–2.0 倍语速和音调；重新生成会替换旧解说轨 |
| 画面调整 | 已完成 | 逐片段结构化保存基础调色、缩放和旋转；支持严格校验并导入 2–65 阶 3D `.cube` LUT 到应用私有目录，数据库、撤回/恢复和项目归档保留 LUT 路径；编辑器预览与最终 Transformer 导出均通过同一个 `CubeLutEffectFactory` 创建 Media3 `SingleColorLut` GPU 变换，解析异常、APK 编译和设备测试契约均已覆盖 |
| 关键帧 | 已完成 | 支持在播放头写入任意多个缩放、旋转、水平/垂直位置、透明度和 0–200% 音量关键帧，Media3 按帧/采样插值，音量可与基础增益及淡入淡出共同工作；关键帧曲线编辑器支持线性/缓入/缓出/缓入缓出并按曲线真实导出 |
| 多轨、音频波形、素材放置 | 已完成 | 图片/视频素材支持位置、缩放和真实 GPU 叠加；音频素材及视频片段原声支持 0-200% 音量、等功率淡入淡出、真实 PCM 峰值、持续过载/静音告警和安全音量建议，以及轨道静音与独奏 |
| 特效模板、动态字幕、转场 | 已完成 | 6 类提示、逐字动态字幕、片段边界淡入淡出、特效模板库（保存/应用/删除、应用时合并去重、JSON 导出/导入）和重叠交叉转场（Media3 合成器平滑 alpha 交叉淡化，支持多个转场并与视频素材叠加）均已接入真实 GPU 导出 |
| 导出预设、任务、下载结果 | 已完成 | 任务、进度、取消、重试、播放和共享已实现；720p/1080p/原画质、自定义参数、能力探测后的 HEVC、横竖屏平台画布真实接入 Media3，预设写入任务历史；整片与单镜头导出均已接线 MOV 封装、ProRes 422 与电影感曲线（内置 arm64 FFmpeg），真机验证（FfmpegDeviceTest）已通过 |
| 素材库、标签、派生与预览 | 已完成 | 本地导入、缩略图、名称/类型/标签检索、移除、引用关系、图片/视频/音频完整预览，以及从片段派生封面素材（自动加“派生/封面”标签）均已实现 |
| 公共素材搜索与素材站导航 | 已完成 | Wikimedia Commons / Openverse 匿名搜索与 Pexels / Pixabay 加密 API Key 搜索已实现；下载前强制查看来源、确认许可，Bilibili 候选素材额外确认上传者授权或合理使用依据；下载支持进度、取消、`.part` 清理、512 MB 上限、原子落盘和素材库失败回滚，并持久保存作者、来源页与许可元数据 |
| 镜头文本/图像搜索与片段导出 | 已完成 | 镜头文本检索（名称/字幕/解说/特效提示）当前为关键词检索并明确标注，语义向量检索需另装 embedding 模型、未安装时不会宣称语义能力；镜头定位和独立片段导出已实现；图像相似度检索已接入 MobileNet 特征 + 余弦相似度（ImageSearchIndex），真机验证通过（ImageSearchDeviceTest：MobileNet 真实提取特征并正确检索匹配镜头） |
| 平台导入与下载任务 | 基础完成 | HTTP/HTTPS 媒体直链支持手机端进度、稳定断点文件、HTTP Range 恢复、服务器不支持 Range 时安全从零覆盖、原子落盘、类型和存储检查，并可建立项目；网页解析与格式选择已实现；Bilibili 登录助手与 cookies.txt 内存会话已接入。`verify-android-platform-import.ps1 -RequireDeviceEvidence` 会强制检查 Bilibili/抖音/快手/YouTube 的登录→解析→选格式→取消→恢复→建立项目证据；当前无连接设备与真实账号证据，因此仍保持 partial，不以代码契约代替真机验收 |
| 合集与片段排序 | 已完成 | 跨项目加入片段、SQLite 持久化、上下排序、移除以及生成独立剪辑项目均已实现 |
| 转写、画面理解、文案、配音 | 已完成（本地模型） | Android 系统中文 TTS 可生成真实 WAV 并自动进入分镜混音；系统语音识别（离线可用时动态标记为本地可用）与 ML Kit 端侧图像标签已接入 AI 设置页；Whisper 自动字幕已接入片段面板与一键自动流水线（FFmpeg 抽 WAV → 端侧转写 → 写分镜字幕）；GPT-2 分镜文案生成已接入片段面板与自动流水线；MobileNet 画面分类与图像相似度检索已接入；模型随 assets/models 打包，首启自动复制 |
| AI 设置、用量和模型检查 | 已完成 | 端侧引擎检查页只展示已安装可运行引擎，未安装能力明确标注；云端 AI 设置支持加密保存并“测试连接（真实请求所选服务商）”；离线 TTS 音色列表与项目/版本/导出/素材/数据库用量已实现 |
| 任务启动、取消、重试与阶段恢复 | 已完成 | 项目流水线六阶段（导入/修剪/分镜/配音素材/版本/渲染）持久化并可从当前阶段恢复；导出、下载与 TTS 支持真实停止，重试创建新任务且不覆盖终态；智能增强工具箱提供“自动匹配素材（按任务推荐开放素材）”与“自动规划特效并渲染（按内容类别写入特效提示后导出）” |
| 诊断、日志导出、存储清理 | 已完成 | 设备/CPU/内存/存储/媒体编码器诊断、无凭据报告复制与分享、安全缓存清理，以及最多 2000 条 / 1 MiB 的 JSONL 结构化运行日志查看/导出/清空均已实现 |
| 使用引导、更新公告 | 已完成 | 首次启动引导、可重开的完整操作引导和与 Android 实际版本一致的更新公告已实现 |
| 未来版本规划 | 已完成 | 设置页提供与本体一致的 1.0–5.0 路线图（自动剪辑、AI 剧情分镜工作台、AI 动画剧场/Meme、多创作者风格、AI 创意助手） |
| 签名发行、升级与真机兼容 | 基础完成 | 正式构建默认强制生产密钥，GitHub 发布工作流要求四项签名 Secret，并归档 APK、SHA-256 清单、证书指纹及 R8 mapping/usage/resources；测试密钥只能显式用于本地非发布审计，严格门禁会拒绝其清单。生产密钥尚未配置，2.2.4→当前版本的数据库/工程/媒体真机升级证据仍缺，因此保持 partial |

## 当前优先补齐项

1. 平台导入：完成真实账号确认后的直链下载和真机逐页验收。
2. 发行：使用生产密钥完成真机升级矩阵；CI 已在同一版本修订上构建 Web/后端与 Android release 产物。

端侧 ONNX session 现按模型文件复用；模型文件大小或修改时间改变时会关闭旧 session 并重新加载。运行线程限制为最多 2 个 intra-op 和 1 个 inter-op，输入 tensor 在每次推理后显式关闭，避免连续分镜分析反复加载模型或累积原生内存。

## 撤回/恢复覆盖矩阵

| 操作 | 状态 | 验证 |
|---|---|---|
| 删除片段 | 已有 | TimelineCommandsTest.deleteUndoRedo |
| 移动片段 | 已有 | TimelineCommandsTest.moveUndoRedo |
| 替换 | 已有 | TimelineCommandsTest.replaceUndoRedo |
| 分割 | 已有 | TimelineCommandsTest.splitUndoRedo |
| 字幕编辑 | 已有 | ProjectControllerTest 与 SnapshotProjectStateTest 覆盖分镜字幕和项目级精确字幕 |
| 音频修改 | 已有 | SnapshotProjectStateTest 与 ProjectControllerTest 覆盖音量、淡入淡出和轨道静音/独奏 |
| 特效修改 | 已有 | SnapshotProjectStateTest 与 ProjectControllerTest 覆盖关键帧、视觉参数和特效模板 |

规则保持：最多 50 步历史（CommandHistory.DEFAULT_LIMIT）；撤回/恢复只还原项目快照，不重建播放器或刷新整个交互页面。

## 导出链路测试覆盖

| 场景 | 测试证据 |
|---|---|
| 生命周期与终态保护 | ExportStateMachineTest 覆盖 prepare/run/complete/cancel/fail/reset 与终态不可覆盖 |
| 取消 | ExportManagerTest.cancelInvokesRunnerAndReachesCancelled 真实调用 runner.cancel 并进入 CANCELLED |
| 进度 | ExportManagerTest.reportsProgressFromRealRunner 通过 runner 轮询上报百分比 |
| 错误状态 | ExportManagerTest.runnerErrorFailsExport 与 abortMarksInterruptedFailure 覆盖 FAILED |
| 完成 | ExportManagerTest.runnerCompletionCompletesExport 覆盖 COMPLETED |
| 中断恢复 | ExportManagerTest.abortMarksInterruptedFailure 与 ExportRecoveryPolicyTest 只清理中断且应用自有目录输出 |
| 多片段时间线 | ExportTimelinePlanTest 验证 V1 主轨跨 V2/A1 片段仍按全局时间轴定位 |
| 设备端多片段导出 | DeviceExportSmokeTest.multiClipExportCompletesOnDevice 在 API 34 模拟器上真实执行双片段 Media3 Transformer 导出并校验输出文件 |
| 设备端取消 | DeviceExportSmokeTest.cancelStopsExportOnDevice 真实调用 Transformer.cancel，取消回调缺失时由 5 秒兜底进入 CANCELLED |

CI 的 `android-emulator` job 会在 API 35 x86_64 模拟器上运行核心
`connectedDebugAndroidTest` 门禁，覆盖加密 AI 凭据、生产 SQLite schema/持久化、测试模型复制和
ONNX 缺失模型错误路径。独立 `Android heavy device farm` 工作流每天定时或手动触发，下载完整
ONNX 模型并构建模型版 APK，在 Firebase Test Lab 设备矩阵上执行 Media3 Transformer、内置 arm64
FFmpeg、完整视觉/文本 ONNX session 和强制 4K 播放压力测试；构建附件和工作流元数据保留 30 天，
Test Lab 真机报告按对应 Firebase 项目策略保留。工作流要求通过
OIDC 配置 `FIREBASE_PROJECT_ID`、`GOOGLE_WORKLOAD_IDENTITY_PROVIDER` 和 `GOOGLE_SERVICE_ACCOUNT`，
缺失任何配置都会明确失败，不会把普通 x86 模拟器或跳过结果冒充真机证明。

规则校验：`scripts/verify-android-guardrails.ps1` 可重复检查禁止宣称、云端/凭据模式与 50 步历史限制。

能力状态统一：`AiCapabilityState` 作为唯一可信状态源，区分可用/本地可用/需要云端/未实现/禁用；UI 不再直接依赖单一 boolean。

架构拆分：`MainActivityActions` 已拆为 Page/Edit/Export 三个 Actions；`TaskDialogController` 已拆为 Project/Timeline 两个对话框控制器，主类只保留装配与委托。
