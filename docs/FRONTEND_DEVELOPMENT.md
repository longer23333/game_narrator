# 前端开发

GameNarrator 采用前后端分离的开发结构：

- `frontend/`：Vite 前端工程，开发端口默认为 `5173`。
- Spring Boot：REST API、数据库和媒体处理后端，IntelliJ 开发端口为 `8082`。
- `src/main/resources/static/`：Vite 生产构建输出，供 Spring Boot 一体化部署。

## 开发启动

先从 IntelliJ 启动 `GameNarratorApplication`，再在项目根目录执行：

```powershell
npm run dev
```

浏览器访问 `http://localhost:5173`。Vite 会把 `/api` 请求代理到
`http://localhost:8082`，因此不需要启用跨域。

如需修改端口，复制 `frontend/.env.example` 为 `frontend/.env.local`：

```text
VITE_BACKEND_URL=http://localhost:8082
VITE_DEV_PORT=5173
VITE_PREVIEW_PORT=4173
```

## 首次安装

仓库已经包含 `frontend/package-lock.json`。重新安装依赖时执行：

```powershell
npm run install:frontend
```

## 生产构建

```powershell
npm run build
.\mvnw.cmd package
```

前端构建会清理并重新生成 `src/main/resources/static/`。Spring Boot 打包后仍可作为
单个应用运行，浏览器直接访问后端地址即可。

## 目录职责

只在 `frontend/index.html` 和 `frontend/public/` 中修改前端源码。不要直接修改
`src/main/resources/static/`，该目录是构建产物，下次执行 `npm run build` 时会被覆盖。

## 首屏加载与登录恢复

- 素材库进入视口前不请求素材列表和来源目录，B站推荐同步在浏览器空闲时执行，避免与任务列表争抢首屏资源。
- 媒体导入成功后会记住权利确认状态以及对应平台已启用登录助手。刷新后会重新向浏览器扩展申请当前登录态。
- 浏览器 Cookie 和临时认证令牌不会写入 `localStorage`、数据库或日志；持久化内容只包含平台域名和启用标记。
