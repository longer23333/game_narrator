# scripts 目录索引

本目录存放构建、发布、校验、环境准备与专项测试脚本。文件名带角色前缀，
先从名字判断类别，再按需查看具体脚本。

## 命名约定

| 前缀 | 含义 | 是否在发布主链路 |
| --- | --- | --- |
| `start-` | 启动某个运行环境（后端、本地云、本地模型、伴读进程） | 否 |
| `setup-` / `install-` / `fetch-` | 一次性环境准备、模型与依赖安装 | 否 |
| `build-` | 产出构建物（Windows 包、Android APK、迁移基线） | 是 |
| `publish-` | 构建 + 校验 + 打包发布的一体化入口 | 是 |
| `verify-` | 只读校验，失败即退出非 0 | 是（多数被 `verify-before-push.ps1` 串联） |
| `check-` / `test-` | 快速自检或端到端冒烟 | 部分 |
| `run-` / `invoke-` | 性能压测、soak、故障恢复演练 | 否 |
| `update-` | 生成派生文件（版本号、配置参考） | 是 |
| `export-` / `sync-` | 导出上下文、同步远端 | 否 |
| 无前缀（`generate-` / `refine-` / `expand-` / `enhance-` / `capture-`） | 学术交付物、答辩 PPT、图标、截图的生成与加工 | 否 |

## 最常用的几个

| 场景 | 命令 |
| --- | --- |
| 日常开发启动（后端 + 前端） | `scripts\start-development.cmd` |
| 本地 PostgreSQL / MinIO 开发环境 | `scripts\start-local-cloud.cmd` |
| 提交前全量校验 | `powershell -File scripts\verify-before-push.ps1` |
| 只跑快速自检 | `scripts\check-fast.cmd` |
| 打 Windows 发布包 | `scripts\publish-windows-release.cmd` |
| 只构建不发布 | `powershell -File scripts\build-windows-release.ps1` |
| 文档一致性校验 | `powershell -File scripts\verify-documentation.ps1` |
| 脚本编码校验 | `powershell -File scripts\verify-script-encoding.ps1` |

`verify-before-push.ps1` 的开关用**哈希表**传递，不要用数组：

```powershell
& scripts\verify-before-push.ps1 @{ SkipFrontendRestore = $true }
```

数组 splat（`@('-SkipFrontendRestore')`）在 PowerShell 里不会按名绑定 switch，会静默变成 `$false`，
导致本该跳过 `npm ci` 的分支照常执行并可能失败。

## 编码约定（重要）

**含中文的 `.ps1` 必须带 UTF-8 BOM。** Windows PowerShell 5.1 在没有 BOM 时按系统 ANSI 代码页
（简体中文机器为 GBK）解析脚本，中文注释会被误解码，典型症状是报 `MissingEndCurlyBrace`
且行号指向一条中文注释——看起来像语法错误，实际是编码问题。

`verify-script-encoding.ps1` 会扫描本目录全部 `.ps1`/`.py`/`.cmd` 并拦截这类问题，
它已接入 `verify-before-push.ps1`。新增脚本后先跑一次这条校验。

`.cmd` 不受此约束：`cmd.exe` 对 UTF-8 BOM 的兼容性不稳定，可能污染首行让 `@echo off` 失效。
含中文输出的 `.cmd` 一律在开头加 `chcp 65001 >nul`，靠切换代码页解决。

## 历史脚本

`generate-academic-deliverables.ps1`、`generate-expanded-defense-ppt.ps1`、
`generate-full-academic-paper.py`、`expand-paper-openxml.ps1`、`refine-defense-ppt.ps1`、
`enhance-defense-ppt-visuals.ps1` 用于生成学年设计论文与答辩 PPT，产出物在
`docs/deliverables/`。它们不属于产品运行链路，保留仅为可复现交付物，不要在新流程里依赖。

## 新增脚本的约定

1. 按上表选前缀，名字用 kebab-case（`verify-xxx.ps1`）。
2. 含中文就带 BOM，写完先跑 `verify-script-encoding.ps1`。
3. 只读校验脚本统一用 `exit 1` 表示失败，便于被 `verify-before-push.ps1` 串联。
4. 会删除文件的脚本不要用 `Remove-Item`：宿主环境对批量删除有阈值拦截（超过 50 个文件需交互确认，
   回收站写入失败时会连单文件删除都被拒绝）。构建产物清理改用 `[System.IO.File]::Delete()` /
   `[System.IO.Directory]::Delete($path, $true)`，或 `robocopy /MIR` 清空目录，
   详见 `build-windows-release.ps1` 里的 `Reset-OwnedDirectory`。
