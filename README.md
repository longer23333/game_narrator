# GameNarrator

多模态游戏视频智能解说与自动剪辑系统。项目使用 Java 21 与 Spring Boot 3.2 从零实现，
不复制其他业务项目的源码、页面、数据库迁移或静态素材。

完整产品范围、功能优先级和验收标准见 [产品需求文档](docs/REQUIREMENTS.md)。
多用户、Token 统计、轻量工程和按需导出方案见 [数据库设计](docs/DATABASE_DESIGN.md)。

## 已实现

- 游戏视频上传与本地安全存储
- 视频任务、九阶段处理流程和游戏事件领域模型
- H2 持久化与 REST API
- 动漫游戏风格的原创任务工作台
- 核心领域单元测试

## 版本演进规划

### 1.0：自动视频剪辑与特效生成

完成可正常使用的自动视频剪辑流程，支持素材分析、片段筛选、文案生成、
AI 配音、时间线编排和成片导出，并逐步覆盖市面上常见的视频特效、字幕、
转场、贴图、音效和画面包装能力。

### 2.0：可编辑的 AI 剧情与分镜工作台

支持用户手动修改 AI 生成的剧情、解说文案和剪辑决策，提供完整的分镜与
时间线展示页面。用户可以调整镜头顺序、片段起止时间、字幕、配音、特效和
剧情节奏，再由系统重新渲染成片。

### 3.0：AI 动画剧场与 Meme 创作

引入本地 AI 绘图和视觉生成能力，根据脚本创作动画剧场所需的角色、场景、
表情和过场画面；结合网络流行文化特征，生成与当前传播语境相符的 Meme、
梗图和视频包装素材。

### 4.0：多创作者风格与一键生成

建立可扩展的创作风格模板系统，支持多个 UP 主类型的节奏、文案、配音、
字幕、特效和镜头组织方式。在避免直接复制具体作品的前提下，提取可描述的
风格特征，并提供从原始素材到成片的一键生成能力。

### 5.0：AI 创意助手与智能素材规划

增加 AI 创意助手。用户输入游戏、动漫、生活片段或大致创作要求后，系统可
生成多套脚本方案供选择，并为选定方案给出所需画面、镜头类型、台词、音效、
特效和素材清单。对于游戏或动画类主题，系统还能在获得合法素材来源和使用
授权的前提下，自动检索、匹配和整理所需素材。

九阶段流程：

1. 素材入库
2. 镜头切分
3. 语音转写
4. 画面理解
5. 高光筛选
6. 解说文案
7. AI 配音
8. 时间线规划
9. 视频合成

## 本地模型建议

针对 RTX 4060 Laptop 8GB 与 16GB 内存，后续适配器优先选择：

- ASR：faster-whisper small / medium（独立 Python 推理服务）
- VLM：Qwen2.5-VL 3B 量化版
- LLM：Qwen2.5 7B Q4
- TTS：GPT-SoVITS 或 Bert-VITS2
- 视频处理：FFmpeg

Java 服务通过 HTTP 调用这些本地推理服务，避免在 JVM 内直接加载显存模型。

## 运行

项目包含 Maven Wrapper，不需要全局安装 Maven：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

首次启用本地语音转写时，在 PowerShell 中执行：

```powershell
.\scripts\setup-whisper.ps1
```

脚本会安装 whisper.cpp 和多语言 `base` 模型到 Git 忽略的 `tools/`、`models/`
目录。启动后访问 `/api/debug/health`，确认 `whisperAvailable` 为 `true`。

首次启用画面理解时执行：

```powershell
.\scripts\setup-vision-model.ps1
```

脚本会安装 Ollama 并下载 `qwen2.5vl:3b` 本地视觉模型。启动后确认健康检查中的
`visionModelAvailable` 为 `true`。

浏览器访问 `http://localhost:8081`。如果需要临时使用其他端口：

```powershell
$env:SERVER_PORT=8090
.\mvnw.cmd spring-boot:run
```

数据默认写入 `data/`，上传视频写入 `storage/`，两者均不提交到 Git。

## 调试错误

控制台与 `logs/game-narrator.log` 会输出每个请求的 `traceId`、耗时、上传文件元数据、
任务 ID 和完整异常堆栈。前端错误信息也会显示相同的追踪号。

需要临时增加日志时：

```powershell
$env:GAME_NARRATOR_LOG_LEVEL="TRACE"
$env:HIBERNATE_SQL_LOG_LEVEL="DEBUG"
.\mvnw.cmd spring-boot:run
```

定位某次错误：

```powershell
Select-String -Path .\logs\game-narrator.log -Pattern "trace=前端显示的追踪号"
```

运行后访问 `http://localhost:8081/api/debug/health`，可以检查 Java 版本、可用内存、
存储目录写权限和 FFmpeg 是否可调用。

如果 FFmpeg 没有加入 PATH，也可以显式指定：

```powershell
$env:FFMPEG_COMMAND="C:\path\to\ffmpeg.exe"
.\mvnw.cmd spring-boot:run
```

## 论文创新点建议

- 融合音频强度、视觉事件置信度和语义重要性的高光评分方法
- 针对游戏类别自适应的解说文案规划
- 解说语速、句子边界与高光片段时长的对齐算法
- 本地开源模型在消费级显卡上的速度、显存与质量对比实验
