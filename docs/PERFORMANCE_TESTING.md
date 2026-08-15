# 性能与负载测试

> 本文同时维护跨电脑兼容审计结论。性能证据必须记录硬件、系统、Java/FFmpeg 版本、输入、命令、原始日志和提交哈希；开发机结果不得冒充目标机或生产证据。

## 可移植性基线

- 外部工具路径配置化并在启动诊断中校验，不依赖开发机绝对路径。
- CPU、内存、磁盘和外部进程并发有上限；失败后释放资源并保留可重试状态。
- 长视频测试必须使用真实 FFmpeg 输出，缺少工具时按契约明确失败或跳过，不得用 marker 文件伪造通过。
- 发布证据区分日常自动化、目标机 GPU OOM 恢复、至少 60 分钟长跑和真实生产验收。

日常 CI 运行 `ConcurrentApiPerformanceTest`：12 个并发工作线程执行 240 次任务列表查询，要求 P95 小于 750 ms，强制 GC 后保留堆增长小于 32 MiB。它用于尽早发现 H2 锁竞争、查询退化和明显内存滞留。

后端 CI 会安装 FFmpeg，并强制执行事件窗口抽帧、授权音效混音、透明 Meme 叠加和时间线转场等真实媒体输出测试。上述测试缺少 FFmpeg/ffprobe 时必须失败，不能以 assumption 跳过；`verify-performance-suite.ps1` 会阻止静默跳过逻辑重新进入这些测试。

每次 CI 都运行 `LongVideoPerformanceIT`，并作为同提交发布证据的阻断依赖：真实 FFmpeg 生成并转码 300 秒、1920×1080 视频，要求处理时间小于 600 秒，即不超过视频时长 2 倍，并要求 Java 保留堆增长小于 64 MiB。测试作业显式设置 `RUN_LONG_VIDEO_PERFORMANCE=true`，不得通过作业条件或 assumption 跳过。

多用户/多任务 HTTP 压测使用 `performance/jmeter/api-load.jmx`，覆盖任务创建、任务列表和导出预设查询：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-performance-load.ps1 `
  -JMeter C:\tools\apache-jmeter\bin\jmeter.bat -Users 12 -DurationSeconds 300 `
  -VideoFile .\samples\five-seconds.mp4 -MaximumErrorPercent 1 -MaximumP95Milliseconds 5000
```

服务须由测试人员单独启动并使用隔离数据库/存储目录。结果写入 `target/performance/summary.json` 和 HTML 报告。40 GB 输入及 GPU 耗尽场景不进入公共 CI：在目标 RTX 4060 8 GB 设备上使用同一脚本和资源指标端点执行，保留报告、JFR/JProfiler 快照和驱动日志；不得将真实视频或含凭据的报告提交到仓库。

正式发布前在目标机运行 `run-release-performance-gate.ps1`，必须传入带 CUDA PyTorch 的 Python 和真实 FFmpeg 路径。脚本以稀疏文件验证 40GB 路径，以配置低余量确认磁盘守卫拒绝语义，强制终止 FFmpeg 并要求非零退出；仓库内置脚本会实际耗尽 CUDA 显存、释放缓存并执行一次恢复后计算，持续至少 60 分钟重复真实音视频转码，并运行九阶段流水线的产物损坏后恢复集成测试。调用方不能再传入只打印 marker 的任意命令。

证据 schema v3 记录 runner 与三个场景实现的 SHA-256、每项耗时、目标机身份、原始日志相对路径和日志 SHA-256。`verify-release-performance-evidence.ps1` 会重新计算脚本与日志哈希，要求六项均通过、清单对应当前提交、长跑不少于 3600 秒、机器含 GPU/OS 标识且不超过 7 天。生成的日志位于 `artifacts/release-performance-reports`，不得包含真实媒体或凭据，也不提交仓库。
