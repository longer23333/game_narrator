# 系统架构

> 文档与源码映射由 `docs/DOCUMENTATION_SOURCE_MAP.json` 声明，并由 `scripts/verify-documentation.ps1` 校验。

## 产品入口与部署边界

- Spring Boot 后端负责任务状态、流水线编排、文件管理、账号隔离与可观测性。
- Web UI 采用 Vue 3 + Pinia 渐进承接遗留页面；任务流由独立 SSE 服务驱动。
- Windows 入口使用 WinForms + WebView2，启动器统一管理后端、本地模型和托盘生命周期。
- Android 使用独立端侧实现，但版本、能力基线和发布日志与 Web/Windows 统一校验。
- 桌面版只监听 loopback；PostgreSQL 云部署默认要求登录，并应位于 HTTPS 反向代理之后。

## 核心分层

- `task.domain`：视频任务和处理阶段等领域状态。
- `task.application`：任务用例、命令和输出视图。
- `task.repository`：JPA 持久化边界。
- `task.web`：任务 REST API 与 SSE。
- `pipeline`：九阶段流水线、恢复、取消、优先队列和资源准入。
- `storage`：上传、容量预算、安全路径和产物生命周期。
- `identity`：当前用户、会话、资源归属和密钥轮换。
- `common`：进程执行、错误响应、追踪与通用安全边界。

## 统一模型服务边界

所有推理能力实现 `ModelAdapter`，业务层只依赖四个标准接口：ASR 的 `Transcriber`、VLM 的
`VisionAnalyzer`、LLM 的 `TextGenerator` 和 TTS 的 `VoiceSynthesizer`。

- Whisper.cpp 由 `WhisperCppTranscriber` 直接实现转写接口。
- Ollama 视觉由 `OllamaVisionClient` 直接实现视觉分析接口。
- 本地/云端文本路由通过 `AdaptiveTextGeneratorAdapter` 接入。
- Piper 由 `PiperVoiceGenerator` 直接实现语音合成接口。

`AdaptiveAiChatClient` 只负责本地/云端路由和活动模型，不再实现具体协议推理。模型选择由
`game-narrator.ai.asr-engine`、`vlm-engine`、`llm-engine`、`tts-engine` 配置完成。

## 任务、并发与资源

完整流水线提交到 `PrioritizedTaskExecutor`。优先级持久化到
`video_tasks.processing_priority`，同优先级保持先进先出。`TaskProcessRegistry` 统一跟踪外部进程、
取消状态以及 CPU、内存、显存租约；`TaskAdmissionController` 先调用 `StorageCapacityGuard`，再按优先级
等待资源预算。预算、活动数和等待数通过 Micrometer 暴露。

## 数据、账号与密钥

- Flyway 历史迁移不可修改；新装基线位于独立 baseline 目录。
- 任务、素材、导出与临时媒体资源都绑定当前用户或 HTTP Session。
- API Key 使用带版本前缀的 AES-256-GCM 密文；`SecretRotationService` 以事务方式重加密旧版本密文。
- 桌面匿名账号保持 `USER` 权限；云部署使用正式会话认证并默认要求登录。

## 编辑与产物一致性

编辑器提交、撤销、重做、版本检出和流水线产物注册均使用项目版本条件更新。并发写入返回冲突，
不会静默覆盖另一条修订链。任务状态通过 SSE 增量推送，断线后指数退避重连。

## 媒体导入边界

- 本地文件通过受管上传进入任务存储。
- Bilibili、YouTube、抖音和 TikTok 等平台 URL 统一由 `YtDlpMediaImporter` 调用 yt-dlp 完成解析、预览、字幕和下载，不再维护平台专用登录或页面抓取导入链路。
- 登录受限内容只接受用户主动上传的 Netscape `cookies.txt`；服务端按 URL 对应平台过滤域名，以会话令牌引用临时文件，并在下载完成或一小时后删除。
- 浏览器扩展只同步 Bilibili 页面可见的推荐/搜索结果为待确认素材候选，不读取 Cookie，也不绕过素材权利确认。

## 关键设计文档

- [数据库设计](DATABASE_DESIGN.md)
- [开发、账号与云迁移](DEVELOPMENT_OPERATIONS.md)
- [性能与负载测试](PERFORMANCE_TESTING.md)
- [Android 功能对齐](ANDROID_FEATURE_PARITY.md)
- [发布流程](RELEASE_PROCESS.md)

## 专题能力边界

- AI 导演评审会保留角色建议、轮次与用户确认；模型输出不能越过人工确认直接发布。
- 素材库以本地上传和 Wikimedia 为主要来源；Bilibili 只作为需权利确认的视频候选。标签、许可、归因和派生关系必须可追溯。
- 切片合集、风格模板和手动编辑器都进入项目修订。模板只保存参数、不携带用户媒体；无 AI 模式仍支持监视、精剪、时间线、批量保存与撤销/重做。
