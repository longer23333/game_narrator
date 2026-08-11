# 前端开发

GameNarrator 开发环境由两个进程组成：

- Vite 前端：`http://127.0.0.1:5173`
- Spring Boot 后端：`http://127.0.0.1:8081`

`npm run dev` 只启动前端。后端没有运行时，Vite 会为 `/api/*` 输出 `ECONNREFUSED`。

## 推荐启动方式

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

只在 `frontend/index.html` 和 `frontend/public/` 修改前端源码。`src/main/resources/static/` 是生产构建产物，会被 `npm run build` 重新生成。
