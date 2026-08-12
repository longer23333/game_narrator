# GameNarrator 环境变量配置参考

本文档由 `scripts/update-configuration-reference.ps1` 根据当前配置文件和桌面启动器生成。请勿手工维护表格。

## 使用原则

- 修改环境变量后需要重启后端或桌面程序；账号级 Pexels/Pixabay Key 可在后台即时修改。
- 标记为“敏感”的值不得提交到 Git、日志、DeepSeek 上下文或前端代码。
- 桌面安装版会自动设置运行目录、端口和并发参数，普通用户不需要手工配置这些项目。
- PostgreSQL、对象存储和云部署的操作步骤另见 `docs/POSTGRESQL_AND_CLOUD_SYNC.md`。

## 完整清单（146 项）

| 环境变量 | Spring 配置路径/用途 | 默认值 | 敏感 | 来源 |
|---|---|---|---|---|
| `AI_ASR_ENGINE` | game-narrator.ai.asr-engine | `whisper-cpp` | 否 | application.yml |
| `AI_LLM_ENGINE` | game-narrator.ai.llm-engine | `adaptive-chat` | 否 | application.yml |
| `AI_RETRY_INITIAL_BACKOFF_MS` | game-narrator.ai.retry.initial-backoff-ms | `250` | 否 | application.yml |
| `AI_RETRY_MAX_ATTEMPTS` | game-narrator.ai.retry.max-attempts | `3` | 否 | application.yml |
| `AI_TTS_ENGINE` | game-narrator.ai.tts-engine | `piper` | 否 | application.yml |
| `AI_VLM_ENGINE` | game-narrator.ai.vlm-engine | `ollama` | 否 | application.yml |
| `ANALYSIS_RETRY_ATTEMPTS` | game-narrator.retry.analysis-attempts | `2` | 否 | application.yml |
| `ASSET_FEATURED_PAGE_SIZE` | game-narrator.asset-library.featured-page-size | `3` | 否 | application.yml |
| `ASSET_LIBRARY_REQUEST_TIMEOUT_SECONDS` | game-narrator.asset-library.request-timeout-seconds | `4` | 否 | application.yml |
| `ASSET_QUERY_AI_TIMEOUT_SECONDS` | game-narrator.asset-library.query-expansion.ai-timeout-seconds | `2` | 否 | application.yml |
| `ASSET_QUERY_CACHE_HOURS` | game-narrator.asset-library.query-expansion.cache-hours | `12` | 否 | application.yml |
| `ASSET_QUERY_CACHE_MAX_ENTRIES` | game-narrator.asset-library.query-expansion.cache-max-entries | `256` | 否 | application.yml |
| `ASSET_SEARCH_CACHE_FRESH_MINUTES` | game-narrator.asset-library.search-resilience.fresh-ttl-minutes | `10` | 否 | application.yml |
| `ASSET_SEARCH_CACHE_MAXIMUM_ENTRIES` | game-narrator.asset-library.search-resilience.maximum-entries | `256` | 否 | application.yml |
| `ASSET_SEARCH_CACHE_STALE_HOURS` | game-narrator.asset-library.search-resilience.stale-if-error-hours | `6` | 否 | application.yml |
| `ASSET_SEARCH_INITIAL_BACKOFF_MILLIS` | game-narrator.asset-library.search-resilience.initial-backoff-millis | `200` | 否 | application.yml |
| `ASSET_SEARCH_MAX_ATTEMPTS` | game-narrator.asset-library.search-resilience.max-attempts | `3` | 否 | application.yml |
| `ASSET_SEMANTIC_MINIMUM_SCORE` | game-narrator.asset-library.semantic-search.minimum-score | `0.28` | 否 | application.yml |
| `ASSET_SEMANTIC_MODEL` | game-narrator.asset-library.semantic-search.model | `bge-m3` | 否 | application.yml |
| `ASSET_SEMANTIC_RESULT_LIMIT` | game-narrator.asset-library.semantic-search.result-limit | `60` | 否 | application.yml |
| `ASSET_SEMANTIC_SEARCH_ENABLED` | game-narrator.asset-library.semantic-search.enabled | `true` | 否 | application.yml |
| `ASSET_SEMANTIC_TIMEOUT_SECONDS` | game-narrator.asset-library.semantic-search.timeout-seconds | `8` | 否 | application.yml |
| `ASYNC_CORE_POOL_SIZE` | game-narrator.async.core-pool-size / 桌面启动器自动注入 | `2 / 按设备运行时计算` | 否 | application.yml, launcher/Program.cs |
| `ASYNC_MAX_POOL_SIZE` | game-narrator.async.max-pool-size / 桌面启动器自动注入 | `2 / 按设备运行时计算` | 否 | application.yml, launcher/Program.cs |
| `ASYNC_QUEUE_CAPACITY` | game-narrator.async.queue-capacity | `10` | 否 | application.yml |
| `BILIBILI_ASSET_SEARCH_BASE_URL` | game-narrator.asset-library.bilibili.base-url | `https://api.bilibili.com` | 否 | application.yml |
| `BILIBILI_ASSET_SEARCH_ENABLED` | game-narrator.asset-library.bilibili.enabled | `true` | 否 | application.yml |
| `BILIBILI_ASSET_SEARCH_MIN_INTERVAL_MS` | game-narrator.asset-library.bilibili.min-request-interval-ms | `800` | 否 | application.yml |
| `CLEANUP_INTERVAL_MS` | game-narrator.cleanup.interval-ms | `3600000` | 否 | application.yml |
| `CLEANUP_RETENTION_HOURS` | game-narrator.cleanup.retention-hours | `24` | 否 | application.yml |
| `CLOUD_IMAGE_POLICY` | game-narrator.ai.cloud-image-policy | `CLOUD_ALLOWED` | 否 | application.yml |
| `CLOUD_SYNC_ENABLED` | game-narrator.cloud-sync.enabled | `false` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_INTERVAL_MS` | game-narrator.cloud-sync.interval-ms | `5000` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_MAX_ATTEMPTS` | game-narrator.cloud-sync.retry.max-attempts | `8` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_MULTIPART_PART_BYTES` | game-narrator.cloud-sync.multipart-part-bytes | `16777216` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_MULTIPART_THRESHOLD_BYTES` | game-narrator.cloud-sync.multipart-threshold-bytes | `67108864` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_RETRY_BASE_SECONDS` | game-narrator.cloud-sync.retry.base-delay-seconds | `30` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_RETRY_JITTER_RATIO` | game-narrator.cloud-sync.retry.jitter-ratio | `0.2` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_RETRY_MAX_SECONDS` | game-narrator.cloud-sync.retry.max-delay-seconds | `3600` | 否 | application-postgresql.yml |
| `DOWNLOAD_RETRY_ATTEMPTS` | game-narrator.retry.download-attempts | `3` | 否 | application.yml |
| `EDITOR_WAVEFORM_CACHE_MAXIMUM_ENTRIES` | game-narrator.editor.waveform-cache.maximum-entries | `128` | 否 | application.yml |
| `EVENT_SAMPLING_MAXIMUM_SECOND_PASS_FRAMES` | game-narrator.event-sampling.maximum-second-pass-frames | `48` | 否 | application.yml |
| `EVENT_SAMPLING_MAXIMUM_WINDOWS` | game-narrator.event-sampling.maximum-windows | `48` | 否 | application.yml |
| `EVENT_SAMPLING_WINDOW_SECONDS` | game-narrator.event-sampling.window-seconds | `0.35` | 否 | application.yml |
| `FFMPEG_COMMAND` | game-narrator.ffmpeg-command | `./tools/ffmpeg/bin/ffmpeg.exe` | 否 | application.yml |
| `FFMPEG_MAX_CONCURRENT` | game-narrator.external-process.ffmpeg-max-concurrent | `1` | 否 | application.yml |
| `GAME_NARRATOR_AI_API_KEY` | Java @Value 注入 | `无（必须显式设置）` | 是 | src/main/java/cn/longer233/gamenarrator/ai/AiSettingsService.java |
| `GAME_NARRATOR_APP_ROOT` | game-narrator.whisper.executable / game-narrator.piper.executable / game-narrator.ffmpeg-command / game-narrator.whisper.model / game-narrator.media-import.yt-dlp / game-narrator.piper.model / game-narrator.piper.voices.model / 桌面启动器自动注入 | `按设备运行时计算` | 否 | application-lite.yml, application-release.yml, launcher/Program.cs |
| `GAME_NARRATOR_DATA_ROOT` | spring.datasource.url / logging.file.name / game-narrator.storage-root / game-narrator.data-root / 桌面启动器自动注入 | `./data / 按设备运行时计算` | 否 | application-lite.yml, application-release.yml, src/main/java/cn/longer233/gamenarrator/ai/AiSettingsService.java, src/main/java/cn/longer233/gamenarrator/ai/AiUsageService.java, src/main/java/cn/longer233/gamenarrator/identity/LocalSecretCipher.java, launcher/Program.cs |
| `GAME_NARRATOR_LOG_LEVEL` | logging.level.root | `DEBUG` | 否 | application.yml |
| `GAME_NARRATOR_PREVIOUS_SECRET_KEYS` | game-narrator.security.previous-master-keys | `无（必须显式设置）` | 是 | application-postgresql.yml, src/main/java/cn/longer233/gamenarrator/identity/LocalSecretCipher.java |
| `GAME_NARRATOR_SECRET_KEY` | game-narrator.security.master-key | `无（必须显式设置）` | 是 | application-postgresql.yml, src/main/java/cn/longer233/gamenarrator/identity/LocalSecretCipher.java |
| `GAME_NARRATOR_SECRET_KEY_VERSION` | game-narrator.security.master-key-version | `v1` | 是 | application-postgresql.yml, src/main/java/cn/longer233/gamenarrator/identity/LocalSecretCipher.java |
| `GENERATION_RETRY_ATTEMPTS` | game-narrator.retry.generation-attempts | `3` | 否 | application.yml |
| `HIBERNATE_SQL_LOG_LEVEL` | logging.level.root | `WARN` | 否 | application.yml |
| `MAX_VIDEO_REQUEST_SIZE` | spring.servlet.multipart.max-request-size | `101GB` | 否 | application.yml |
| `MAX_VIDEO_UPLOAD_SIZE` | spring.servlet.multipart.max-file-size | `100GB` | 否 | application.yml |
| `MAXIMUM_SCENE_FRAMES` | game-narrator.maximum-scene-frames | `240` | 否 | application.yml |
| `MEDIA_IMPORT_FORCE_IPV4` | game-narrator.media-import.force-ipv4 | `true` | 否 | application.yml |
| `MEDIA_IMPORT_LOCAL_AUTH_DISCOVERY` | game-narrator.media-import.local-authentication-discovery | `false` | 否 | application.yml |
| `MINIMUM_FREE_STORAGE_BYTES` | game-narrator.capacity.minimum-free-bytes | `5368709120` | 否 | application.yml |
| `MINIMUM_FREE_STORAGE_PERCENT` | game-narrator.capacity.minimum-free-percent | `5` | 否 | application.yml |
| `MULTIPART_TEMP_DIRECTORY` | spring.servlet.multipart.location | `./storage/upload-temp` | 否 | application.yml |
| `OBJECT_STORAGE_ACCESS_KEY` | game-narrator.cloud-sync.access-key | `无（必须显式设置）` | 是 | application-postgresql.yml |
| `OBJECT_STORAGE_BUCKET` | game-narrator.cloud-sync.bucket | `game-narrator` | 否 | application-postgresql.yml |
| `OBJECT_STORAGE_ENDPOINT` | game-narrator.cloud-sync.endpoint | `无（必须显式设置）` | 否 | application-postgresql.yml |
| `OBJECT_STORAGE_PATH_STYLE` | game-narrator.cloud-sync.path-style | `true` | 否 | application-postgresql.yml |
| `OBJECT_STORAGE_REGION` | game-narrator.cloud-sync.region | `us-east-1` | 否 | application-postgresql.yml |
| `OBJECT_STORAGE_SECRET_KEY` | game-narrator.cloud-sync.secret-key | `无（必须显式设置）` | 是 | application-postgresql.yml |
| `OCR_ENABLED` | game-narrator.ocr.enabled | `true` | 否 | application.yml |
| `OLLAMA_BASE_URL` | game-narrator.ollama.base-url / 桌面启动器自动注入 | `http://127.0.0.1:11434 / http://localhost:11434 / 按设备运行时计算` | 否 | application-release.yml, application.yml, launcher/Program.cs |
| `OLLAMA_FLASH_ATTENTION` | 桌面启动器自动注入 | `按设备运行时计算` | 否 | launcher/Program.cs |
| `OLLAMA_HOST` | 桌面启动器自动注入 | `按设备运行时计算` | 否 | launcher/Program.cs |
| `OLLAMA_KEEP_ALIVE` | 桌面启动器自动注入 | `按设备运行时计算` | 否 | launcher/Program.cs |
| `OLLAMA_MAX_FRAMES` | game-narrator.ollama.max-frames | `0` | 否 | application.yml |
| `OLLAMA_MAX_LOADED_MODELS` | 桌面启动器自动注入 | `按设备运行时计算` | 否 | launcher/Program.cs |
| `OLLAMA_MODELS` | 桌面启动器自动注入 | `按设备运行时计算` | 否 | launcher/Program.cs |
| `OLLAMA_NUM_PARALLEL` | 桌面启动器自动注入 | `按设备运行时计算` | 否 | launcher/Program.cs |
| `OLLAMA_SCRIPT_MODEL` | game-narrator.ollama.script-model | `${OLLAMA_VISION_MODEL:qwen2.5vl:3b` | 否 | application.yml |
| `OLLAMA_VISION_MODEL` | game-narrator.ollama.vision-model | `qwen2.5vl:3b` | 否 | application.yml |
| `OPENVERSE_BASE_URL` | game-narrator.asset-library.openverse.base-url | `https://api.openverse.org/v1` | 否 | application.yml |
| `OPENVERSE_ENABLED` | game-narrator.asset-library.openverse.enabled | `true` | 否 | application.yml |
| `OTHER_PROCESS_MAX_CONCURRENT` | game-narrator.external-process.other-max-concurrent | `2` | 否 | application.yml |
| `PEXELS_API_KEY` | game-narrator.asset-library.pexels.api-key | `无（必须显式设置）` | 是 | application.yml |
| `PEXELS_BASE_URL` | game-narrator.asset-library.pexels.base-url | `https://api.pexels.com` | 否 | application.yml |
| `PEXELS_ENABLED` | game-narrator.asset-library.pexels.enabled | `true` | 否 | application.yml |
| `PHASE_RETRY_BACKOFF_MS` | game-narrator.retry.initial-backoff-ms | `500` | 否 | application.yml |
| `PIPELINE_WAITING_RETRY_DELAY_MS` | game-narrator.pipeline.waiting-retry-delay-ms | `30000` | 否 | application.yml |
| `PIPELINE_WAITING_RETRY_INITIAL_DELAY_MS` | game-narrator.pipeline.waiting-retry-initial-delay-ms | `30000` | 否 | application.yml |
| `PIPER_DEFAULT_PROFILE` | game-narrator.piper.default-profile | `narrative` | 否 | application.yml |
| `PIPER_DEFAULT_VOICE` | game-narrator.piper.default-voice | `huayan` | 否 | application.yml |
| `PIPER_EXECUTABLE` | game-narrator.piper.executable | `./tools/piper/piper/piper.exe` | 否 | application.yml |
| `PIPER_LENGTH_SCALE` | game-narrator.piper.length-scale | `1.0` | 否 | application.yml |
| `PIPER_MODEL` | game-narrator.piper.model / game-narrator.piper.voices.model | `./models/piper/zh_CN-huayan-medium.onnx` | 否 | application.yml |
| `PIPER_XIAOXIAO_MODEL` | game-narrator.piper.voices.model | `./models/piper/zh_CN-xiaoxiao-medium.onnx` | 否 | application.yml |
| `PIXABAY_API_KEY` | game-narrator.asset-library.pixabay.api-key | `无（必须显式设置）` | 是 | application.yml |
| `PIXABAY_BASE_URL` | game-narrator.asset-library.pixabay.base-url | `https://pixabay.com` | 否 | application.yml |
| `PIXABAY_ENABLED` | game-narrator.asset-library.pixabay.enabled | `true` | 否 | application.yml |
| `POSTGRES_PASSWORD` | spring.datasource.password | `无（必须显式设置）` | 是 | application-postgresql.yml |
| `POSTGRES_URL` | spring.datasource.url | `jdbc:postgresql://127.0.0.1:5432/game_narrator` | 否 | application-postgresql.yml |
| `POSTGRES_USER` | spring.datasource.username | `game_narrator` | 否 | application-postgresql.yml |
| `REQUIRE_LOGIN` | game-narrator.auth.require-login | `true` | 否 | application-postgresql.yml |
| `RESOURCE_CPU_UNITS` | game-narrator.resources.cpu-units | `${ASYNC_MAX_POOL_SIZE:4` | 否 | application.yml |
| `RESOURCE_GPU_MEMORY_BYTES` | game-narrator.resources.gpu-memory-bytes | `7516192768` | 否 | application.yml |
| `RESOURCE_MEMORY_BYTES` | game-narrator.resources.memory-bytes | `0` | 否 | application.yml |
| `RESOURCE_TASK_BASE_MEMORY_BYTES` | game-narrator.resources.per-task.base-memory-bytes | `1073741824` | 否 | application.yml |
| `RESOURCE_TASK_CPU_UNITS` | game-narrator.resources.per-task.cpu-units | `1` | 否 | application.yml |
| `RESOURCE_TASK_GPU_MEMORY_BYTES` | game-narrator.resources.per-task.local-ai-gpu-memory-bytes | `4294967296` | 否 | application.yml |
| `RESOURCE_TASK_MAX_MEMORY_BYTES` | game-narrator.resources.per-task.maximum-memory-bytes | `4294967296` | 否 | application.yml |
| `RESOURCE_TASK_SOURCE_MEMORY_MULTIPLIER` | game-narrator.resources.per-task.source-memory-multiplier | `0.05` | 否 | application.yml |
| `SCENE_ANALYSIS_FPS` | game-narrator.scene-analysis-fps | `6` | 否 | application.yml |
| `SCENE_THRESHOLD` | game-narrator.scene-threshold | `0.35` | 否 | application.yml |
| `SCENE_TIMEOUT_MINUTES` | game-narrator.scene-timeout-minutes | `20` | 否 | application.yml |
| `SERVER_ADDRESS` | server.address | `127.0.0.1` | 否 | application.yml |
| `SERVER_MAX_THREADS` | server.tomcat.threads.max / 桌面启动器自动注入 | `32 / 按设备运行时计算` | 否 | application.yml, launcher/Program.cs |
| `SERVER_MIN_SPARE_THREADS` | server.tomcat.threads.min-spare | `2` | 否 | application.yml |
| `SERVER_PORT` | server.port / 桌面启动器自动注入 | `18081 / 8081 / 按设备运行时计算` | 否 | application-release.yml, application.yml, launcher/Program.cs |
| `SUBTITLE_CHUNK_CHARACTERS` | game-narrator.subtitle.chunk-characters | `4000` | 否 | application.yml |
| `TASK_STREAM_REFRESH_MS` | game-narrator.task-stream.refresh-ms | `1000` | 否 | application.yml |
| `TESSERACT_EXECUTABLE` | game-narrator.ocr.executable | `tesseract` | 否 | application.yml |
| `TESSERACT_LANGUAGES` | game-narrator.ocr.languages | `chi_sim+eng` | 否 | application.yml |
| `THUMBNAIL_ACQUIRE_TIMEOUT_MS` | game-narrator.media-preview.thumbnail-acquire-timeout-ms | `500` | 否 | application.yml |
| `THUMBNAIL_CACHE_ENTRIES` | game-narrator.media-preview.thumbnail-cache-entries | `96` | 否 | application.yml |
| `THUMBNAIL_CACHE_MINUTES` | game-narrator.media-preview.thumbnail-cache-minutes | `30` | 否 | application.yml |
| `THUMBNAIL_FAILURE_CACHE_SECONDS` | game-narrator.media-preview.thumbnail-failure-cache-seconds | `90` | 否 | application.yml |
| `THUMBNAIL_MAX_BYTES` | game-narrator.media-preview.thumbnail-max-bytes | `1572864` | 否 | application.yml |
| `THUMBNAIL_MAX_CONCURRENT` | game-narrator.media-preview.thumbnail-max-concurrent | `4` | 否 | application.yml |
| `USERPROFILE` | game-narrator.media-import.cookie-search-paths | `无（必须显式设置）` | 否 | application.yml |
| `VIDEO_ENCODER` | game-narrator.render.video-encoder | `h264_nvenc` | 否 | application.yml |
| `VIDEO_SEARCH_MAXIMUM_IMAGE_BYTES` | game-narrator.video-search.maximum-image-bytes | `10485760` | 否 | application.yml |
| `VIDEO_WORKING_SPACE_MULTIPLIER` | game-narrator.capacity.working-space-multiplier | `1.5` | 否 | application.yml |
| `VISION_QUALITY_FALLBACK_MODELS` | game-narrator.vision-quality.fallback-models | `无（必须显式设置）` | 否 | application.yml |
| `VISION_QUALITY_MINIMUM_CONFIDENCE` | game-narrator.vision-quality.minimum-confidence | `0.45` | 否 | application.yml |
| `VISION_QUALITY_SAME_MODEL_ATTEMPTS` | game-narrator.vision-quality.same-model-attempts | `2` | 否 | application.yml |
| `WHISPER_CHUNK_MINUTES` | game-narrator.whisper.chunk-minutes | `20` | 否 | application.yml |
| `WHISPER_CHUNK_TIMEOUT_MINUTES` | game-narrator.whisper.chunk-timeout-minutes | `45` | 否 | application.yml |
| `WHISPER_EXECUTABLE` | game-narrator.whisper.executable | `./tools/whisper/Release/whisper-cli.exe` | 否 | application.yml |
| `WHISPER_LANGUAGE` | game-narrator.whisper.language | `auto` | 否 | application.yml |
| `WHISPER_LONG_AUDIO_THRESHOLD_MINUTES` | game-narrator.whisper.long-audio-threshold-minutes | `30` | 否 | application.yml |
| `WHISPER_MAX_CONCURRENT` | game-narrator.external-process.whisper-max-concurrent | `1` | 否 | application.yml |
| `WHISPER_MODEL` | game-narrator.whisper.model | `./models/ggml-base.bin` | 否 | application.yml |
| `WHISPER_THREADS` | game-narrator.whisper.threads / 桌面启动器自动注入 | `8 / 按设备运行时计算` | 否 | application.yml, launcher/Program.cs |
| `WIKIMEDIA_BASE_URL` | game-narrator.asset-library.wikimedia.base-url | `https://commons.wikimedia.org` | 否 | application.yml |
| `WIKIMEDIA_ENABLED` | game-narrator.asset-library.wikimedia.enabled | `true` | 否 | application.yml |
| `WIKIMEDIA_REQUEST_TIMEOUT_SECONDS` | game-narrator.asset-library.wikimedia.request-timeout-seconds | `3` | 否 | application.yml |
| `YT_DLP_EXECUTABLE` | game-narrator.media-import.yt-dlp | `./tools/yt-dlp/yt-dlp.exe` | 否 | application.yml |

## 更新与校验

```powershell
.\scripts\update-configuration-reference.ps1
.\scripts\update-configuration-reference.ps1 -Check
```
