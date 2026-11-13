# 文档索引

本目录是 GameNarrator 的唯一文档根。文档与源码的对应关系由
[DOCUMENTATION_SOURCE_MAP.json](DOCUMENTATION_SOURCE_MAP.json) 声明，由
`scripts\verify-documentation.ps1` 在每次发布前校验：文档不得缺失、映射的源码文件必须存在、
Java 符号必须真实存在、文档内部链接不得断开、复核时间不得超过 120 天。

改动代码却不同步文档，会直接让发布校验失败——这是刻意的约束，不是噪音。

## 按场景查找

| 我要…… | 看这份 |
| --- | --- |
| 知道项目做成什么样、有哪些功能、验收标准是什么 | [产品需求文档](REQUIREMENTS.md) |
| 理解模块划分、九阶段流水线、插件式适配器怎么串起来 | [系统架构](ARCHITECTURE.md) |
| 查表结构、迁移规则、备份与恢复策略 | [数据库设计](DATABASE_DESIGN.md) |
| 配环境变量、改配置、查某个开关的默认值 | [配置参考](CONFIGURATION_REFERENCE.md) |
| 从零装一遍、跑起来、排常见的启动和播放问题 | [安装与配置说明](安装与配置说明.md) |
| 日常开发、日志与可观测性、账号管理、PostgreSQL、云同步 | [开发与运维](DEVELOPMENT_OPERATIONS.md) |
| 发版：改版本号、构建、签名、推送、三端对齐 | [统一发布流程](RELEASE_PROCESS.md) |
| 压测、跨机兼容基线、性能证据怎么留 | [性能与负载测试](PERFORMANCE_TESTING.md) |
| 现场答辩怎么演、按什么顺序点哪些页面 | [答辩演示方案](答辩演示方案.md) |
| 找论文、答辩 PPT、预览图 | [交付物说明](deliverables/交付说明.md) |

## Android 平台

| 文档 | 内容 |
| --- | --- |
| [功能同步矩阵](ANDROID_FEATURE_PARITY.md) | 桌面与 Android 的逐项功能对照，以及"软件完成"与"可正式发布"之间的三类门禁差异 |
| [端侧模型部署](ANDROID_MODELS.md) | 模型目录、`getExternalFilesDir("models")` 的实际读取路径与打包要求 |

## 机器可读数据

| 文件 | 用途 |
| --- | --- |
| `DOCUMENTATION_SOURCE_MAP.json` | 文档 → 源码映射清单，`verify-documentation.ps1` 的输入 |
| `ANDROID_CORE_BASELINE.json` | Android 功能基线（schema v3），机器校验的权威来源 |
| `ANDROID_RELEASE_AUDIT.json` | Android 发布审计结论快照 |
| `ANDROID_PLATFORM_IMPORT_E2E_TEMPLATE.json` | 平台导入端到端测试的输入模板 |
| `ANDROID_UPGRADE_E2E_TEMPLATE.json` | 版本升级端到端测试的输入模板 |
| `RELEASE_PERFORMANCE_EVIDENCE_TEMPLATE.json` | 性能证据记录模板 |

## 子目录

| 目录 | 内容 | 是否入库 |
| --- | --- | --- |
| `deliverables/` | 论文、答辩 PPT、PDF 与预览图，合计约 700MB | 仅 `交付说明.md`；其余按 `.gitignore` 排除 |
| `screenshots/`、`screenshots-device/` | 桌面与真机界面截图 | 是 |

交付物里的 PDF/PPTX/DOCX 都是可再生成的二进制，不入库；需要时用
`scripts\generate-academic-deliverables.ps1` 一类脚本重建，或向维护者索取。
脚本本身的分类、命名约定与编码规则见 [scripts 索引](../scripts/README.md)。

## 新增文档的流程

1. 在 `docs/` 下新建 `.md`，文件名用中文或英文均可，避免空格。
2. **必须**把路径登记进 `DOCUMENTATION_SOURCE_MAP.json` 的 `reviewedDocuments` 数组，否则
   `verify-documentation.ps1` 会以"Document is missing from the human review manifest"失败。
3. 如果这份文档描述具体代码，同时在 `documents` 数组里加一条，写明 `sources`（必须真实存在）
   与 `reviewedOn`。涉及 Java 类型时可用 `symbols` 声明符号名，校验脚本会确认真有这个类型。
4. 跑一次 `powershell -File scripts\verify-documentation.ps1` 确认通过，再提交。
