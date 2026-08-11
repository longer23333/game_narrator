# 前端开发

GameNarrator 开发环境由两个进程组成：

- Vite 前端：`http://127.0.0.1:5173`
- Spring Boot 后端：`http://127.0.0.1:8081`

`npm run dev` 只启动前端。后端没有运行时，Vite 会为 `/api/*` 输出 `ECONNREFUSED`。

## 推荐启动方式

直接双击 `scripts/start-development.cmd`。脚本会检查并自动启动缺失的 Spring Boot 后端和 Vite 前端，等待两者就绪后打开浏览器；重复双击不会重复启动已经正常运行的服务。

桌面安装版页面右上角提供“重新运行”按钮。点击后由桌面启动器统一重新启动 Spring Boot，以及当前选择为本地模式时所需的 Ollama 服务，不需要手工输入命令。Whisper、Piper 和 FFmpeg 属于按任务调用的工具，会由后端自动启动，不需要常驻运行。

如需分别调试两个进程，也可以使用下面的手工方式。

打开两个 PowerShell 终端。

终端一启动默认 H2 后端：

```powershell
npm run dev:backend
```

需要 PostgreSQL 时改用：

```powershell
npm run dev:backend:postgresql
```

终端二启动前端：

```powershell
npm run dev:frontend
```

浏览器访问 `http://127.0.0.1:5173`。Vite 默认把 `/api` 代理到 `http://127.0.0.1:8081`。

## 自定义端口

复制 `frontend/.env.example` 为 `frontend/.env.local`：

```text
VITE_BACKEND_URL=http://127.0.0.1:8081
VITE_DEV_PORT=5173
VITE_PREVIEW_PORT=4173
```

如果修改后端端口，必须同步修改 `VITE_BACKEND_URL`。

## 安装与构建

```powershell
npm run install:frontend
npm run build
.\mvnw.cmd package
```

前端源码位于 `frontend/index.html`、`frontend/src/` 和 `frontend/public/`。`src/main/resources/static/` 是生产构建产物，会被 `npm run build` 重新生成。

## 渐进式 Vue 架构

- `frontend/src/main.js` 是统一入口，创建 Vue 3 应用和共享 Pinia 实例。
- `stores/tasks.js` 是任务列表与活动队列的唯一状态源；`services/task-stream.js` 独立处理 SSE 快照、增量消息和指数退避重连。
- `TaskList.vue`、`ActiveTaskQueue.vue` 和 `TaskCard.vue` 负责任务 UI，旧 `app.js` 只保留详情、分镜等尚未迁移的交互，并通过 `window.gameNarratorTasks` 刷新 store。
- 素材库与平台导入分别由 `AssetLibraryBoundary.vue`、`MediaImporterBoundary.vue` 按需加载，Vite 会生成独立 chunk；边界内暂时加载原有功能脚本，后续可逐块替换而不改变入口契约。
- `stores/preferences.js` 配合 `pinia-plugin-persistedstate` 统一持久化任务草稿、素材搜索和平台导入偏好，并在首次启动时迁移旧的 localStorage 键。

修改 `.vue`、store 或服务后运行 `npm test` 和 `npm run build`。修改普通 JavaScript 后还需逐个运行 `node --check`。
