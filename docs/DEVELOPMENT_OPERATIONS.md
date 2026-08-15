# 开发与运维

本文是 Web/Windows 开发、可观测性、账号管理、PostgreSQL、云同步与项目上下文导出的唯一操作入口。环境变量以 [配置参考](CONFIGURATION_REFERENCE.md) 为准，表结构以 [数据库设计](DATABASE_DESIGN.md) 为准。

## 本地开发

- 推荐从 Windows 启动器运行；后端调试使用 `./mvnw spring-boot:run`，前端在 `frontend/` 执行 `npm install`、`npm run dev`、`npm test` 与 `npm run build`。
- Vite 只承担热更新，API 由 Spring Boot 提供；生产静态资源由前端构建写入 `src/main/resources/static/`。
- Vue/Pinia 是唯一业务状态源；遗留 DOM 脚本只能是薄入口，不得复制任务状态。

## 账号、管理与容量保护

- 默认是本地账号模式；管理中心维护用户、配额、Token 用量、任务和密钥轮换，密钥只以加密形式落盘。
- 上传、临时文件和任务目录受存储根目录约束。磁盘低水位时拒绝新任务，但允许诊断和清理。
- Prometheus 指标通过 Actuator 暴露；生产环境应监控队列、失败率、阶段耗时、磁盘余量和外部进程错误。

## PostgreSQL、对象存储与云同步

- 本地默认 H2；生产使用 `application-postgresql.yml`。每个 Flyway 迁移必须同时提供 H2 与 PostgreSQL 版本并通过成对检查。
- 云同步是显式开关，关闭时保持纯本地行为。同步以项目、修订、运行和 artifact 为边界，临时文件不是权威状态。
- 大视频通过对象存储传输；数据库只保存元数据、校验和与存储键。迁移 H2 前必须备份数据库和存储目录，并先在副本验证升级。
- 生产验收仍需真实 PostgreSQL、对象存储、网络中断恢复、并发冲突与大文件回归；H2 通过不能替代这些证据。

## 项目上下文与日常门禁

运行 `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/export-deepseek-context.ps1` 更新项目上下文。导出前备份旧索引，导出后执行文档校验。日常门禁包括 `./mvnw test`、前端 `npm test`/`npm run build`、`scripts/verify-documentation.ps1`、`scripts/verify-release-alignment.ps1` 和 Windows 启动器构建。
