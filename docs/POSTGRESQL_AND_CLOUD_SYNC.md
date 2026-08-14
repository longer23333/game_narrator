# PostgreSQL 与跨设备项目同步

## 运行模式

- 默认配置继续使用 H2，适合完全离线的单机安装。
- `postgresql` profile 使用 PostgreSQL；开发和云部署共用 `db/migration-postgresql` 迁移历史。
- 项目、账号、修订和任务状态保存在 PostgreSQL。视频与流水线产物不进入数据库，而是通过 `cloud_sync_item` 同步到 S3 兼容对象存储。
- 客户端恢复文件时先校验 SHA-256，再原子替换本地缓存。

## 生产路径持续验证

每次 GitHub CI 都会启动独立的 PostgreSQL 16 服务，并执行两条数据库构建路径：

1. 从 V1 到当前版本执行完整 `db/migration-postgresql` 历史。
2. 从 `B39__version_2_2_4_baseline.sql` 建库，再执行 V40 至当前版本升级。

两条路径都会执行 Flyway `validate`，并比较最终表列、约束和索引签名；CI 还会通过真实 JDBC 事务验证租户外键、按 owner 隔离更新和 `video_project.version` 乐观锁。`postgresqlIntegration` 是同一提交发布证据的必需门禁。

普通本地测试不会把未运行的 PostgreSQL 集成测试冒充通过。只有显式设置 `RUN_POSTGRESQL_INTEGRATION=true` 并提供独立测试库时才执行；权威证明来自 CI 的 PostgreSQL 服务任务。

## 本地 PostgreSQL

启动 PostgreSQL、MinIO 并以前台方式运行后端：

```powershell
.\scripts\start-local-cloud.cmd
```

只启动基础设施：

```powershell
.\scripts\start-local-cloud.ps1
```

也可只启动 PostgreSQL：

```powershell
docker compose -f docker-compose.postgresql.yml up -d postgres
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgresql"
```

示例环境默认连接 `jdbc:postgresql://127.0.0.1:5432/game_narrator`。生产环境必须覆盖 `POSTGRES_URL`、`POSTGRES_USER` 和 `POSTGRES_PASSWORD`，不得沿用示例密码。

PostgreSQL 迁移由已审核的 H2 历史生成并保持相同版本号。修改迁移后运行：

```powershell
.\scripts\build-postgresql-migrations.ps1
```

## 对象存储

本地 MinIO 验证配置示例：

```powershell
$env:CLOUD_SYNC_ENABLED='true'
$env:OBJECT_STORAGE_ENDPOINT='http://127.0.0.1:9000'
$env:OBJECT_STORAGE_REGION='us-east-1'
$env:OBJECT_STORAGE_BUCKET='game-narrator'
$env:OBJECT_STORAGE_ACCESS_KEY='game_narrator'
$env:OBJECT_STORAGE_SECRET_KEY='change-this-local-password'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgresql"
```

MinIO 控制台默认为 `http://127.0.0.1:9001`，`minio-init` 会创建私有 `game-narrator` bucket。`OBJECT_STORAGE_ENDPOINT` 留空时使用 AWS S3 默认端点；自建服务填写完整 HTTPS 地址，需要虚拟主机寻址时设置 `OBJECT_STORAGE_PATH_STYLE=false`。

## 同步语义

- 新上传的源视频和流水线产物进入 `PENDING`，后台默认每批最多上传 5 个文件。
- 大于 64 MiB 的文件默认按 16 MiB 分片上传。
- 失败原因会持久化，并按 30 秒起步、最长 1 小时的指数退避和抖动重试；可用 `CLOUD_SYNC_MAX_ATTEMPTS`、`CLOUD_SYNC_RETRY_BASE_SECONDS`、`CLOUD_SYNC_RETRY_MAX_SECONDS`、`CLOUD_SYNC_RETRY_JITTER_RATIO` 调整。
- 默认连续失败 8 次后进入 `PERMANENT_FAILURE`，避免无限请求；用户可调用 `POST /api/cloud-sync/{id}/retry`，管理员也可在后台重新排队。
- `GET /api/cloud-sync` 只返回当前登录用户的同步记录。
- 下载先写同目录临时文件，SHA-256 一致后再原子替换。源视频恢复后先更新该设备使用的任务路径，再允许流水线继续。

## 云端部署要求

1. 使用托管 PostgreSQL，并配置时间点恢复备份。
2. 使用私有 S3/OSS/COS/MinIO bucket，启用版本控制和生命周期规则。
3. 设置 `CLOUD_SYNC_ENABLED=true`，所有凭据通过密钥管理系统注入。
4. Spring Boot 服务只通过 HTTPS 反向代理暴露；客户端不得直接连接数据库。
5. 多设备编辑继续使用 `video_project.version` 乐观锁，冲突不得静默覆盖。

## 尚未替代的生产验收

CI 证明迁移兼容性、数据库约束和基础并发语义，但不等同于具体云厂商上的容量、故障切换、备份恢复、连接池上限和跨地域延迟验收。上线前仍须在目标托管 PostgreSQL 与对象存储环境完成这些演练并保存结果。

## 从现有 H2 迁移

当前支持“新 PostgreSQL 环境 + 后续对象同步”。既有 H2 数据应在停机备份后执行一次性 ETL，保留 UUID、账号归属、修订顺序和哈希；不能把 H2 数据文件直接交给 PostgreSQL。
