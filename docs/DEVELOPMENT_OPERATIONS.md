# 开发与运维

本文是 Web/Windows 开发、可观测性、账号管理、PostgreSQL、云同步与项目上下文导出的唯一操作入口。环境变量以 [配置参考](CONFIGURATION_REFERENCE.md) 为准，表结构以 [数据库设计](DATABASE_DESIGN.md) 为准。

## 本地开发

- 推荐从 Windows 启动器运行；后端调试使用 `./mvnw spring-boot:run`，前端在 `frontend/` 执行 `npm install`、`npm run dev`、`npm test` 与 `npm run build`。
- Vite 只承担热更新，API 由 Spring Boot 提供；生产静态资源由前端构建写入 `src/main/resources/static/`。
- Vue/Pinia 是唯一业务状态源；遗留 DOM 脚本只能是薄入口，不得复制任务状态。

### 静态资源缓存与"改了没变化"

缓存策略按文件名是否带内容哈希分成两档，由 `StaticResourceCacheConfig` 实施：

| 路径 | 策略 | 含义 |
| --- | --- | --- |
| `/assets/index-<hash>.js` | `max-age=1y, immutable` | 文件名带内容哈希，内容变则文件名变，可永久缓存 |
| 其余（`/app.js`、`/app.css`、`index.html` 等） | `no-cache` | 每次回源校验，改完刷新即生效 |

排障顺序固定为：先 `curl -s http://127.0.0.1:8081/app.js | grep 关键字` 确认**服务端**是不是新内容，
再怀疑浏览器缓存。二者别混为一谈。

- 开发运行时应用读的是 `target/classes/static`，改完源码要 `mvnw process-resources`（或复制）才生效。
- 改了 `app.css` 记得同步 bump `index.html` 里的 `?v=YYYYMMDD-N` 版本串，否则客户端一直用旧样式。
- `index.html` 在 `src/main/resources/static/` 与 `frontend/` 各有一份，改 head（含 `preload` 标签）时两份必须同步。
- Java 类改动必须**重启**应用，8081 常驻实例不做热加载。

## 账号、管理与容量保护

- 默认是本地账号模式；管理中心维护用户、配额、Token 用量、任务和密钥轮换，密钥只以加密形式落盘。
- PostgreSQL 新部署应在首次注册前设置高熵 `ADMINISTRATOR_BOOTSTRAP_TOKEN`，并仅通过受信通道把它提交为注册请求的 `bootstrapToken` 字段；令牌匹配且系统尚无注册管理员时才授予 `ADMIN`。未配置或不匹配时，包括首个注册账号在内都只能成为普通用户。完成引导后应从运行环境移除令牌并重启服务。
- 登录失败默认按“规范化用户名 + 来源地址/客户端”累计；5 次失败锁定 15 分钟，成功登录清除对应记录。可通过配置参考中的 `LOGIN_*` 变量调整，但生产环境不应关闭边界代理的连接限速。
- 上传、临时文件和任务目录受存储根目录约束。磁盘低水位时拒绝新任务，但允许诊断和清理。
- Prometheus 指标通过 Actuator 暴露；生产环境应监控队列、失败率、阶段耗时、磁盘余量和外部进程错误。

## PostgreSQL、对象存储与云同步

- 本地默认 H2；生产使用 `application-postgresql.yml`。每个 Flyway 迁移必须同时提供 H2 与 PostgreSQL 版本并通过成对检查。
- 云同步是显式开关，关闭时保持纯本地行为。同步以项目、修订、运行和 artifact 为边界，临时文件不是权威状态。
- 大视频通过对象存储传输；数据库只保存元数据、校验和与存储键。迁移 H2 前必须备份数据库和存储目录，并先在副本验证升级。
- 生产验收仍需真实 PostgreSQL、对象存储、网络中断恢复、并发冲突与大文件回归；H2 通过不能替代这些证据。

## 项目上下文与日常门禁

运行 `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/export-deepseek-context.ps1` 更新项目上下文。导出前备份旧索引，导出后执行文档校验。

提交前的统一入口是 `scripts\verify-before-push.ps1`，它按顺序串起：生成配置参考校验 → 版本与能力对齐 → Android 护栏 → **脚本编码校验** → 前端构建与语法检查 → 前后端测试并行。它等价于下列单项：

- `./mvnw test` 与前端 `npm test` / `npm run build`
- `scripts/verify-documentation.ps1`（文档必须登记进 `DOCUMENTATION_SOURCE_MAP.json`，链接不得断开）
- `scripts/verify-release-alignment.ps1`
- `scripts/verify-script-encoding.ps1`（含中文的 `.ps1` 必须带 UTF-8 BOM，详见 `scripts/README.md`）
- Windows 启动器构建

该脚本的开关用哈希表传递（`@{ SkipFrontendRestore = $true }`），不要用数组 splat。发版走 `scripts\publish-windows-release.cmd`，它在此基础上追加打包、哈希边车与冷启动冒烟，流程见 [统一发布流程](RELEASE_PROCESS.md)。
