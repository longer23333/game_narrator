# PostgreSQL 与跨设备项目同步

## 运行模式

- 默认配置继续使用 H2，适合完全离线的单机安装。
- `postgresql` profile 使用 PostgreSQL。开发机和正式云端使用同一种数据库及同一套
  `db/migration-postgresql` Flyway 迁移，避免上线时临时改表。
- 项目、账号、修订和任务状态直接保存在 PostgreSQL；所有电脑连接同一云端 API 后，
  同一账号会立即看到相同项目列表。
- 大文件不进入数据库。源视频和流水线产物通过 `cloud_sync_item` 上传到 S3 兼容对象存储，
  另一台电脑首次预览或继续任务时会校验 SHA-256 后原子写入本地缓存。

## 本地 PostgreSQL

启动 PostgreSQL：

```powershell
docker compose -f docker-compose.postgresql.yml up -d postgres
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgresql"
```

默认连接为 `jdbc:postgresql://127.0.0.1:5432/game_narrator`，账号和密码均为
`game_narrator`。真实环境必须通过 `POSTGRES_URL`、`POSTGRES_USER`、
`POSTGRES_PASSWORD` 覆盖，不要沿用示例密码。

PostgreSQL 迁移由以下命令从已审核的 H2 历史迁移生成；它保留版本号，转换 `CLOB` 和
PostgreSQL 不支持的约束语法，并跳过只针对旧 H2 数据文件的 V2 回填：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-postgresql-migrations.ps1
```

## 本地验证对象存储

```powershell
docker compose -f docker-compose.postgresql.yml up -d
$env:CLOUD_SYNC_ENABLED='true'
$env:OBJECT_STORAGE_ENDPOINT='http://127.0.0.1:9000'
$env:OBJECT_STORAGE_REGION='us-east-1'
$env:OBJECT_STORAGE_BUCKET='game-narrator'
$env:OBJECT_STORAGE_ACCESS_KEY='game_narrator'
$env:OBJECT_STORAGE_SECRET_KEY='change-this-local-password'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgresql"
```

MinIO 控制台为 `http://127.0.0.1:9001`。`minio-init` 会创建 `game-narrator` bucket。

## 云端部署

1. 部署一个 PostgreSQL 实例并设置三个 `POSTGRES_*` 环境变量。
2. 创建私有 S3/OSS/COS/MinIO bucket，设置所有 `OBJECT_STORAGE_*` 变量。
3. 设置 `CLOUD_SYNC_ENABLED=true`，通过 HTTPS 暴露这一个 Spring Boot 服务。
4. 桌面端和浏览器都访问该 HTTPS 地址；不要让不同电脑直接连接数据库。
5. 对数据库做时间点恢复备份，对 bucket 开启版本控制和生命周期规则。

`OBJECT_STORAGE_ENDPOINT` 留空时使用 AWS S3 默认端点；自建 MinIO 或兼容服务时填写完整
HTTPS 地址。云端应关闭 path-style 时设置 `OBJECT_STORAGE_PATH_STYLE=false`。

## 同步语义

- 新上传源视频以及新登记的流水线产物会进入 `PENDING`，后台最多每批上传 5 个文件。
- 大于 64 MiB 的文件默认以 16 MiB 分片上传；失败会记录原因并在 5 分钟后重试。
- 成功后状态为 `SYNCED`。`GET /api/cloud-sync` 只返回当前登录用户的同步记录。
- 下载先写同目录临时文件，SHA-256 一致后再原子替换目标文件。
- 源视频恢复后会更新该设备使用的任务路径，然后才启动流水线。
- 多设备同时编辑项目仍由 `video_project.version` 乐观锁处理；冲突不会静默覆盖。

## 从现有 H2 迁移

当前实现为“新 PostgreSQL 环境 + 后续对象同步”路径。已有 H2 数据迁移应在停机备份后执行
一次性 ETL，保留 UUID、账号归属、修订顺序和哈希；不要把 H2 数据文件直接交给 PostgreSQL。
