# 性能与负载测试

日常 CI 运行 `ConcurrentApiPerformanceTest`：12 个并发工作线程执行 240 次任务列表查询，要求 P95 小于 750 ms，强制 GC 后保留堆增长小于 32 MiB。它用于尽早发现 H2 锁竞争、查询退化和明显内存滞留。

每周六定时 CI（或提交信息包含 `[performance]`）运行 `LongVideoPerformanceIT`：真实 FFmpeg 生成并转码 300 秒、1920×1080 视频，要求处理时间小于 600 秒，即不超过视频时长 2 倍，并要求 Java 保留堆增长小于 64 MiB。测试需显式设置 `RUN_LONG_VIDEO_PERFORMANCE=true`，不会伪装成快速单元测试。

多用户/多任务 HTTP 压测使用 `performance/jmeter/api-load.jmx`，覆盖任务创建、任务列表和导出预设查询：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-performance-load.ps1 `
  -JMeter C:\tools\apache-jmeter\bin\jmeter.bat -Users 12 -DurationSeconds 300 `
  -VideoFile .\samples\five-seconds.mp4 -MaximumErrorPercent 1 -MaximumP95Milliseconds 5000
```

服务须由测试人员单独启动并使用隔离数据库/存储目录。结果写入 `target/performance/summary.json` 和 HTML 报告。40 GB 输入及 GPU 耗尽场景不进入公共 CI：在目标 RTX 4060 8 GB 设备上使用同一脚本和资源指标端点执行，保留报告、JFR/JProfiler 快照和驱动日志；不得将真实视频或含凭据的报告提交到仓库。

正式发布前在目标机运行 `run-release-performance-gate.ps1`。脚本以稀疏文件验证 40GB 路径，执行磁盘余量、GPU OOM、FFmpeg 中断、长跑与恢复命令，并只把报告 SHA-256 写入证据清单。`verify-release-performance-evidence.ps1` 要求六项均通过、清单对应当前提交、机器含 GPU/OS 标识且不超过 7 天；原始媒体、凭据与大体积报告不得提交。
