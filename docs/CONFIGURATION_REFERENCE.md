# GameNarrator 环境变量配置参考

本文档由 `scripts/update-configuration-reference.ps1` 根据当前配置文件和桌面启动器生成。请勿手工维护表格。

## 使用原则

- 修改环境变量后需要重启后端或桌面程序；账号级 Pexels/Pixabay Key 可在后台即时修改。
- 标记为“敏感”的值不得提交到 Git、日志、DeepSeek 上下文或前端代码。
- 桌面安装版会自动设置运行目录、端口和并发参数，普通用户不需要手工配置这些项目。
- PostgreSQL、对象存储和云部署的操作步骤另见 `docs/POSTGRESQL_AND_CLOUD_SYNC.md`。

## 完整清单（70 项）

| 环境变量 | Spring 配置路径/用途 | 默认值 | 敏感 | 来源 |
|---|---|---|---|---|
| `ASSET_SEMANTIC_SEARCH_ENABLED` | game-narrator.asset-library.semantic-search.enabled | `true` | 否 | application.yml |
| `ASYNC_CORE_POOL_SIZE` | game-narrator.async.core-pool-size / 桌面启动器自动注入 | `2 / 按设备运行时计算` | 否 | application.yml, launcher/Program.cs |
| `ASYNC_MAX_POOL_SIZE` | game-narrator.async.max-pool-size / 桌面启动器自动注入 | `2 / 按设备运行时计算` | 否 | application.yml, launcher/Program.cs |
| `ASYNC_QUEUE_CAPACITY` | game-narrator.async.queue-capacity | `10` | 否 | application.yml |
| `CLOUD_SYNC_ENABLED` | game-narrator.cloud-sync.enabled | `false` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_MAX_ATTEMPTS` | game-narrator.cloud-sync.retry.max-attempts | `8` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_RETRY_BASE_SECONDS` | game-narrator.cloud-sync.retry.base-delay-seconds | `30` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_RETRY_JITTER_RATIO` | game-narrator.cloud-sync.retry.jitter-ratio | `0.2` | 否 | application-postgresql.yml |
| `CLOUD_SYNC_RETRY_MAX_SECONDS` | game-narrator.cloud-sync.retry.max-delay-seconds | `3600` | 否 | application-postgresql.yml |
| `FFMPEG_COMMAND` | game-narrator.ffmpeg-command | `./tools/ffmpeg/bin/ffmpeg.exe` | 否 | application.yml |
| `FFMPEG_MAX_CONCURRENT` | game-narrator.external-process.ffmpeg-max-concurrent | `1` | 否 | application.yml |
| `GAME_NARRATOR_AI_API_KEY` | Java @Value 注入 | `无（必须显式设置）` | 是 | src/main/java/cn/longer233/gamenarrator/ai/AiSettingsService.java |
| `GAME_NARRATOR_APP_ROOT` | game-narrator.whisper.executable / game-narrator.piper.executable / game-narrator.ffmpeg-command / game-narrator.whisper.model / game-narrator.media-import.yt-dlp / game-narrator.piper.model / game-narrator.piper.voices.model / 桌面启动器自动注入 | `按设备运行时计算` | 否 | application-lite.yml, application-release.yml, launcher/Program.cs |
| `GAME_NARRATOR_DATA_ROOT` | spring.datasource.url / logging.file.name / game-narrator.storage-root / game-narrator.data-root / 桌面启动器自动注入 | `./data / 按设备运行时计算` | 否 | application-lite.yml, application-release.yml, src/main/java/cn/longer233/gamenarrator/ai/AiSettingsService.java, src/main/java/cn/longer233/gamenarrator/ai/AiUsageService.java, src/main/java/cn/longer233/gamenarrator/identity/LocalSecretCipher.java, launcher/Program.cs |
| `GAME_NARRATOR_LOG_LEVEL` | logging.level.root | `DEBUG` | 否 | application.yml |
| `GAME_NARRATOR_PREVIOUS_SECRET_KEYS` | game-narrator.security.previous-master-keys | `无（必须显式设置）` | 是 | application-postgresql.yml, src/main/java/cn/longer233/gamenarrator/identity/LocalSecretCipher.java |
| `GAME_NARRATOR_SECRET_KEY` | game-narrator.security.master-key | `无（必须显式设置）` | 是 | application-postgresql.yml, src/main/java/cn/longer233/gamenarrator/identity/LocalSecretCipher.java |
| `GAME_NARRATOR_SECRET_KEY_VERSION` | game-narrator.security.master-key-version | `v1` | 是 | application-postgresql.yml, src/main/java/cn/longer233/gamenarrator/identity/LocalSecretCipher.java |
| `HIBERNATE_SQL_LOG_LEVEL` | logging.level.root | `WARN` | 否 | application.yml |
| `MAX_VIDEO_REQUEST_SIZE` | spring.servlet.multipart.max-request-size | `101GB` | 否 | application.yml |
| `MAX_VIDEO_UPLOAD_SIZE` | spring.servlet.multipart.max-file-size | `100GB` | 否 | application.yml |
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
| `OTHER_PROCESS_MAX_CONCURRENT` | game-narrator.external-process.other-max-concurrent | `2` | 否 | application.yml |
| `PEXELS_API_KEY` | game-narrator.asset-library.pexels.api-key | `无（必须显式设置）` | 是 | application.yml |
| `PIPER_EXECUTABLE` | game-narrator.piper.executable | `./tools/piper/piper/piper.exe` | 否 | application.yml |
| `PIPER_MODEL` | game-narrator.piper.model / game-narrator.piper.voices.model | `./models/piper/zh_CN-huayan-medium.onnx` | 否 | application.yml |
| `PIPER_XIAOXIAO_MODEL` | game-narrator.piper.voices.model | `./models/piper/zh_CN-xiaoxiao-medium.onnx` | 否 | application.yml |
| `PIXABAY_API_KEY` | game-narrator.asset-library.pixabay.api-key | `无（必须显式设置）` | 是 | application.yml |
| `POSTGRES_PASSWORD` | spring.datasource.password | `无（必须显式设置）` | 是 | application-postgresql.yml |
| `POSTGRES_URL` | spring.datasource.url | `jdbc:postgresql://127.0.0.1:5432/game_narrator` | 否 | application-postgresql.yml |
| `POSTGRES_USER` | spring.datasource.username | `game_narrator` | 否 | application-postgresql.yml |
| `REQUIRE_LOGIN` | game-narrator.auth.require-login | `true` | 否 | application-postgresql.yml |
| `SERVER_ADDRESS` | server.address | `127.0.0.1` | 否 | application.yml |
| `SERVER_MAX_THREADS` | server.tomcat.threads.max / 桌面启动器自动注入 | `32 / 按设备运行时计算` | 否 | application.yml, launcher/Program.cs |
| `SERVER_MIN_SPARE_THREADS` | server.tomcat.threads.min-spare | `2` | 否 | application.yml |
| `SERVER_PORT` | server.port / 桌面启动器自动注入 | `18081 / 8081 / 按设备运行时计算` | 否 | application-release.yml, application.yml, launcher/Program.cs |
| `TESSERACT_EXECUTABLE` | game-narrator.ocr.executable | `tesseract` | 否 | application.yml |
| `TESSERACT_LANGUAGES` | game-narrator.ocr.languages | `chi_sim+eng` | 否 | application.yml |
| `USERPROFILE` | game-narrator.media-import.cookie-search-paths | `无（必须显式设置）` | 否 | application.yml |
| `VIDEO_ENCODER` | game-narrator.render.video-encoder | `h264_nvenc` | 否 | application.yml |
| `VIDEO_WORKING_SPACE_MULTIPLIER` | game-narrator.capacity.working-space-multiplier | `1.5` | 否 | application.yml |
| `VISION_QUALITY_FALLBACK_MODELS` | game-narrator.vision-quality.fallback-models | `无（必须显式设置）` | 否 | application.yml |
| `WHISPER_CHUNK_MINUTES` | game-narrator.whisper.chunk-minutes | `20` | 否 | application.yml |
| `WHISPER_CHUNK_TIMEOUT_MINUTES` | game-narrator.whisper.chunk-timeout-minutes | `45` | 否 | application.yml |
| `WHISPER_EXECUTABLE` | game-narrator.whisper.executable | `./tools/whisper/Release/whisper-cli.exe` | 否 | application.yml |
| `WHISPER_LANGUAGE` | game-narrator.whisper.language | `auto` | 否 | application.yml |
| `WHISPER_LONG_AUDIO_THRESHOLD_MINUTES` | game-narrator.whisper.long-audio-threshold-minutes | `30` | 否 | application.yml |
| `WHISPER_MAX_CONCURRENT` | game-narrator.external-process.whisper-max-concurrent | `1` | 否 | application.yml |
| `WHISPER_MODEL` | game-narrator.whisper.model | `./models/ggml-base.bin` | 否 | application.yml |
| `WHISPER_THREADS` | game-narrator.whisper.threads / 桌面启动器自动注入 | `8 / 按设备运行时计算` | 否 | application.yml, launcher/Program.cs |
| `YT_DLP_EXECUTABLE` | game-narrator.media-import.yt-dlp | `./tools/yt-dlp/yt-dlp.exe` | 否 | application.yml |

## 更新与校验

```powershell
.\scripts\update-configuration-reference.ps1
.\scripts\update-configuration-reference.ps1 -Check
```
