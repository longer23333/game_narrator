# 数据库迁移与恢复策略

- `db/migration` 与 `db/migration-postgresql` 中的 V1–V38 是已发布、不可修改的升级历史；现有安装继续逐版校验和升级。
- `db/baseline-h2` 与 `db/baseline-postgresql` 是由 `scripts/build-migration-baselines.ps1` 生成的新装快照。仅在确认空库时显式配置该目录，不能与历史目录同时加载。
- 每次新增迁移后先运行 `scripts/build-postgresql-migrations.ps1`，再运行 `scripts/build-migration-baselines.ps1 -Check`。CI 会拒绝缺号、过期或 H2 基线与完整历史 schema 不一致的变更。
- Flyway Community 不依赖 Undo。生产回退采用“迁移前一致性备份 → 新库恢复 → 校验 → 切换连接”的可恢复流程；禁止在失败库上逆向猜测 DDL。
- 发布前必须演练恢复。`DatabaseMigrationTest.backupRestoreDrillRecoversPreMigrationDataAndSchema` 验证 V37 备份可恢复数据且不会带入 V38 字段。

新装 H2 可将 Flyway location 显式设为 `classpath:db/baseline-h2`；PostgreSQL 对应使用 `classpath:db/baseline-postgresql`。完成基线后，后续版本再切回对应历史迁移目录并从 V39 继续。
