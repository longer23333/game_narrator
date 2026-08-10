# 系统架构

## 分层

- `task.domain`：视频任务、处理阶段、游戏事件等领域对象。
- `task.application`：任务用例、输入命令和输出视图。
- `task.repository`：JPA 持久化接口。
- `task.web`：REST API。
- `storage`：上传视频存储边界。
- `common`：统一异常响应。

## 产品入口

- Windows 安装版以 WinForms + WebView2 提供桌面应用窗口。启动器负责后端、可选本地模型、应用窗口和托盘的统一生命周期，不再自动打开默认浏览器。
- 桌面窗口只允许导航到当前随机本机端口；外部链接交给系统浏览器，避免把远程页面加载到拥有本机 API 访问能力的应用容器中。
- Web 页面保留为移动端/远程部署入口。页面中的相对 `/api` 始终指向提供页面的后端，不能收藏安装版的 `127.0.0.1` 地址作为跨设备云端入口。

## 模型服务边界

Spring Boot 负责业务状态、任务编排、实验记录和文件管理。ASR、VLM、LLM、TTS
以独立本地推理进程运行，通过标准 HTTP 适配器接入。这样既方便替换免费开源模型，
也避免 Python/CUDA 依赖污染 Java 主工程。

云端模型由统一自适应客户端接入：OpenAI 兼容协议覆盖 OpenAI、DashScope、DeepSeek、
OpenRouter、SiliconFlow、Kimi、智谱、火山方舟、千帆、混元、MiniMax、xAI、Mistral、
Groq、Together、Perplexity 和 Cerebras；Anthropic Messages 与 Google Gemini 使用原生
协议适配。每次响应只记录输入、输出、缓存 Token、模型和按用户配置单价计算的费用，
不记录提示词、图片或模型响应内容。每日费用写入 `data/config/ai-usage.json`，会话统计
仅保留在当前进程内。

## 并发与前端刷新

- `video_project.current_revision_id` 的编辑器提交、撤销、重做、版本检出和流水线产物登记均以 `version` 条件更新；并发写入返回冲突，不允许静默覆盖另一条修订链。
- Web 任务状态通过 SSE 推送，断线后指数退避重连，不保留无效轮询钩子。

## 下一阶段

1. 增加 FFmpeg 元数据读取与镜头切分执行器。
2. 定义 `InferenceAdapter` 接口及四类模型适配器。
3. 实现高光评分与候选片段去重。
4. 实现文案时间预算和配音对齐。
5. 使用异步任务线程池与 SSE 推送进度。
# 可解释事件层

高光筛选与文案生成之间新增独立的事件事实层：

1. `GameEventTimelineService` 读取视觉分析和高光清单，并按可配置知识包生成候选事件及证据。
2. 候选事件以 `AI_SUGGESTED` 状态写入 `game_events`，人工修正后保留，不被后续自动重建静默覆盖。
3. 分镜工作台通过事件 API 展示、修正和确认事件。
4. `OllamaScriptGenerator` 只接收已确认事件及中性的片段时间范围，避免未确认的 OCR、模型描述或高光标签被写成事实。
5. 用户触发事实约束重写后，后续配音、时间线和渲染产物按现有失效规则重新生成。
