# 性能与跨电脑兼容审计

本轮对启动器、任务管线、外部进程、媒体预处理、AI、素材库、前端轮询、安装配置和数据路径进行了项目级静态审计，并结合发行版日志复现实际故障。

## 已处理的高影响问题

- 镜头提取改为 6 FPS、480 像素宽的低分辨率时域采样，避免对 60/120 FPS 视频逐帧做场景计算。
- 镜头截图改用单线程 PNG 编码，规避部分 FFmpeg 构建在 VFR/MJPEG 下的 `frame_rate 0/0` 初始化失败。
- 没有转场的短视频自动提取首帧，不再把“没有场景切换”当作失败。
- 限制每个任务最多 240 张场景图，并把场景处理超时从 60 分钟缩短为可配置的 20 分钟。
- 工具错误统一截断后再写数据库，保证任何失败都能进入 `FAILED`，不会停留在处理中。
- FFmpeg、Whisper 等外部进程使用独立虚拟线程排空输出，避免被网络任务占满公共线程池后发生管道阻塞。
- Whisper 使用统一的进程树终止与真实超时机制。
- 本地抠图启用 VP9 realtime 参数，并具有进程树终止、输出上限和两分钟超时。
- 启动器按 CPU 核心数设置 Whisper 与任务并发；本地 Ollama 限制单模型、单并发，减少低内存电脑抖动。
- 运行中的前端轮询由 3 秒调为 5 秒，空闲调为 60 秒，AI 用量调为 15 秒，减少后端和 H2 无效查询。
- 发行版日志默认从 DEBUG 调为 INFO，减少磁盘写入。
- 删除无意义的“待审核/待翻译素材”占位标签，并通过 Flyway 清理已有数据。

## 跨电脑策略

- 安装包限定 Windows x64，并捆绑 Java、FFmpeg、Whisper、Piper、yt-dlp 和 WebView2 安装器，不依赖用户 PATH。
- 所有发行路径从安装目录与 `GAME_NARRATOR_DATA_ROOT` 推导，未发现用户机器盘符或用户名硬编码。
- FFmpeg 默认使用软件解码和自动 CPU 线程，避免强制 CUDA、QSV、AMF 在不同显卡上启动失败。
- 网络素材与云端 AI 均有超时和本地/浏览器回退；实际速度仍受用户网络、平台风控和 API 限流影响。
- 本地模型仅在用户选择本地 AI 时安装；低内存电脑建议使用云端 AI。

## 保留的取舍

- 没有默认启用硬件编码。自动猜测显卡驱动容易在其他电脑产生黑屏、花屏或编码器不可用；后续应在诊断通过后作为可选性能档开放。
- 静态前端继续禁用长缓存，因为当前公共脚本文件名未带内容哈希，长缓存会导致升级后仍加载旧代码。
- 多个视频任务默认偏向少量并发，让单任务尽快完成并避免多个 FFmpeg/AI 同时占满内存。
## 2026-08-03 memory and automatic asset pass

- Desktop Java heap is now bounded by detected memory: 512 MB on smaller machines, 768 MB on 8 GB-class machines, and 1024 MB on 16 GB+ machines. G1 starts at 64 MB instead of reserving a large heap immediately.
- Local Ollama keeps one model and one parallel request, enables flash attention, and unloads idle models after two minutes.
- Remote thumbnail cache is bounded to 24 entries of at most 1.5 MB, with a ten-minute TTL. The previous theoretical retained-byte ceiling was much larger.
- Completed media download jobs expire after 30 minutes and the in-memory registry is capped at 64 retained jobs.
- Release H2 page cache is capped at 16 MB and embedded Tomcat worker threads are bounded.
- Automatic storyboard assignment is idempotent for existing segment/type placements, prefers the best matching downloaded local asset, and bounds automatic additions to eight visual assets, four sound effects, and one background track per project.
- Selected video, image, green-screen, sound-effect, and background-music assets are consumed by the existing FFmpeg render graph: duration trimming/looping, scaling/cropping, chroma-key overlay, timed SFX mixing, and ducked full-program BGM are applied automatically.
