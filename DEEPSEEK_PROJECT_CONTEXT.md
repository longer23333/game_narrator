# GameNarrator — DeepSeek 结构化项目索引

> 自动生成：2026-08-11 11:19:25 +08:00；UTF-8 BOM；索引不再内嵌全部源码。
> Git：分支 `main`，提交 `a3817c2`；收录文件 694 个，分卷 8 个。

## 使用方法

1. 始终先提供本索引。只按任务额外提供 1–3 个 `deepseek-context/*.md` 分卷。
2. 后端任务选 01+02，测试再加 03；Web 选 01+04；Android 选 01+05（测试再加 06）；Windows/发布选 01+07；需求审查选 01+08。
3. `frontend/` 是 Web 权威源码；`src/main/resources/static/` 是 Vite 构建产物，已排除以避免重复和旧产物误导。
4. 判断冲突的强制优先级：**Flyway 迁移 > 当前实现源码与运行配置 > 测试 > 架构/需求文档 > 构建产物或历史说明**。
5. 分卷仍可能较大；复杂任务应先让模型依据本索引列出所需文件，再仅发送对应文件原文。

## 模块职责

| 模块 | 权威目录 | 职责 |
|---|---|---|
| Spring Boot | `src/main/java` | 任务、流水线、事件、剪辑、渲染、导入、诊断与 API |
| 数据库 | `src/main/resources/db/migration` | Flyway 追加迁移，是表结构最高权威 |
| Web 源码 | `frontend` | Vite 输入；构建后复制到 Spring static |
| Android | `android-app` | Android 客户端、Gradle、Manifest 与版本 |
| Windows | `launcher`、`release` | 桌面启动器、安装包与发布脚本 |
| CI/CD | `.github` | GitHub 自动化、构建和发布 |
| 测试 | `src/test` | 行为证据；不能替代当前实现或迁移 |

## 源码分卷

| 分卷 | 文件数 | 字符数 | 建议用途 |
|---|---:|---:|---|
| `deepseek-context/01-foundation.md` | 78 | 119801 | 构建、配置、资源、数据库迁移 |
| `deepseek-context/02-backend.md` | 258 | 1025816 | Spring 后端实现 |
| `deepseek-context/03-tests.md` | 81 | 226998 | Spring 测试与行为验证 |
| `deepseek-context/04-frontend.md` | 17 | 357777 | Vite Web 源码 |
| `deepseek-context/05-android-main.md` | 151 | 855263 | Android 实现、资源与 Gradle |
| `deepseek-context/06-android-tests.md` | 55 | 131882 | Android 单元及设备测试 |
| `deepseek-context/07-platform-release.md` | 10 | 49452 | Windows、CI/CD 与发布 |
| `deepseek-context/08-docs-scripts.md` | 44 | 155274 | 需求、架构、维护脚本 |

## 当前 Git 工作区（最近修改）

```text
M launcher/Program.cs
```

## Flyway 迁移索引

- `src/main/resources/db/migration/V1__database_v2_foundation.sql`
- `src/main/resources/db/migration/V2__backfill_legacy_project_history.sql`
- `src/main/resources/db/migration/V3__external_asset_catalog.sql`
- `src/main/resources/db/migration/V4__asset_library_organization.sql`
- `src/main/resources/db/migration/V5__asset_semantic_embeddings.sql`
- `src/main/resources/db/migration/V6__asset_chinese_localization.sql`
- `src/main/resources/db/migration/V7__storyboard_review.sql`
- `src/main/resources/db/migration/V8__video_segment_semantic_index.sql`
- `src/main/resources/db/migration/V9__video_segment_image_hash.sql`
- `src/main/resources/db/migration/V10__allow_storyboard_review_task_status.sql`
- `src/main/resources/db/migration/V11__repair_bilibili_scraped_titles_and_tags.sql`
- `src/main/resources/db/migration/V12__storyboard_asset_placement.sql`
- `src/main/resources/db/migration/V13__remove_placeholder_asset_labels.sql`
- `src/main/resources/db/migration/V14__optional_ai_pipeline.sql`
- `src/main/resources/db/migration/V15__automatic_pipeline_mode.sql`
- `src/main/resources/db/migration/V16__editing_scope.sql`
- `src/main/resources/db/migration/V17__task_cancellation.sql`
- `src/main/resources/db/migration/V18__full_pipeline_failures.sql`
- `src/main/resources/db/migration/V19__task_glossary.sql`
- `src/main/resources/db/migration/V20__video_task_optimistic_lock.sql`
- `src/main/resources/db/migration/V21__clip_compilations.sql`
- `src/main/resources/db/migration/V22__project_revision_tree.sql`
- `src/main/resources/db/migration/V23__explainable_game_event_timeline.sql`
- `src/main/resources/db/migration/V24__battle_narrative_plan.sql`
- `src/main/resources/db/migration/V25__knowledge_packs_and_director_profile.sql`
- `src/main/resources/db/migration/V26__source_media_storage_tracking.sql`
- `src/main/resources/db/migration/V27__community_ecosystem.sql`
- `src/main/resources/db/migration/V28__local_accounts_and_admin_center.sql`
- `src/main/resources/db/migration/V29__ai_director_review_board.sql`
- `src/main/resources/db/migration/V30__per_user_asset_library.sql`
- `src/main/resources/db/migration/V31__cloud_sync_execution.sql`
- `src/main/resources/db/migration/V32__task_recycle_bin.sql`
- `src/main/resources/db/migration/V33__cloud_sync_retry_policy.sql`
- `src/main/resources/db/migration/V34__asset_provider_credentials.sql`
- `src/main/resources/db/migration/V35__resumable_asset_downloads.sql`

## 运行配置索引

- `.github/workflows/ci.yml`
- `.github/workflows/mirror-to-gitee.yml`
- `.github/workflows/release-version.yml`
- `android-app/gradle.properties`
- `android-app/gradle/wrapper/gradle-wrapper.properties`
- `src/main/resources/application.yml`
- `src/main/resources/application-lite.yml`
- `src/main/resources/application-postgresql.yml`
- `src/main/resources/application-release.yml`

## API 路由索引（按 Controller 注解静态提取）

| 方法 | 路径 | 来源 |
|---|---|---|
| DELETE | `/api/assets/{id}` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| DELETE | `/api/assets/provider-settings/{provider}` | `src/main/java/cn/longer233/gamenarrator/asset/AssetProviderCredentialController.java` |
| DELETE | `/api/tasks/{id}` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| DELETE | `/api/tasks/{taskId}/storyboard/assets/{placementId}` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| DELETE | `/api/tasks/trash/{id}` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/admin/management/api-usage` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| GET | `/api/admin/management/audit` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| GET | `/api/admin/management/cloud-sync` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| GET | `/api/admin/management/overview` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| GET | `/api/admin/management/projects/{id}` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| GET | `/api/admin/management/projects` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| GET | `/api/admin/management/users/{id}` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| GET | `/api/admin/management/users` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| GET | `/api/admin/storage` | `src/main/java/cn/longer233/gamenarrator/storage/StorageAdminController.java` |
| GET | `/api/ai-settings/usage` | `src/main/java/cn/longer233/gamenarrator/ai/AiSettingsController.java` |
| GET | `/api/ai-settings` | `src/main/java/cn/longer233/gamenarrator/ai/AiSettingsController.java` |
| GET | `/api/assets/{id}/download-status` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| GET | `/api/assets/{id}/preview` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| GET | `/api/assets/{id}/remote-preview` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| GET | `/api/assets/{id}/similar` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| GET | `/api/assets/{id}/thumbnail` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| GET | `/api/assets/discover/featured` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| GET | `/api/assets/provider-settings` | `src/main/java/cn/longer233/gamenarrator/asset/AssetProviderCredentialController.java` |
| GET | `/api/assets/sources` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| GET | `/api/assets` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| GET | `/api/auth/me` | `src/main/java/cn/longer233/gamenarrator/identity/AuthController.java` |
| GET | `/api/cloud-sync` | `src/main/java/cn/longer233/gamenarrator/cloud/CloudSyncController.java` |
| GET | `/api/community/resources/{id}/export` | `src/main/java/cn/longer233/gamenarrator/community/CommunityResourceController.java` |
| GET | `/api/community/resources` | `src/main/java/cn/longer233/gamenarrator/community/CommunityResourceController.java` |
| GET | `/api/compilations/{id}` | `src/main/java/cn/longer233/gamenarrator/compilation/ClipCompilationController.java` |
| GET | `/api/compilations` | `src/main/java/cn/longer233/gamenarrator/compilation/ClipCompilationController.java` |
| GET | `/api/debug/health` | `src/main/java/cn/longer233/gamenarrator/diagnostics/DiagnosticsController.java` |
| GET | `/api/debug/logs/export` | `src/main/java/cn/longer233/gamenarrator/diagnostics/DiagnosticsController.java` |
| GET | `/api/debug/logs` | `src/main/java/cn/longer233/gamenarrator/diagnostics/DiagnosticsController.java` |
| GET | `/api/director-profile` | `src/main/java/cn/longer233/gamenarrator/personalization/DirectorProfileController.java` |
| GET | `/api/effect-presets/{code}/export` | `src/main/java/cn/longer233/gamenarrator/effect/EffectController.java` |
| GET | `/api/effect-presets` | `src/main/java/cn/longer233/gamenarrator/effect/EffectController.java` |
| GET | `/api/export-presets` | `src/main/java/cn/longer233/gamenarrator/export/ExportController.java` |
| GET | `/api/exports/{jobId}/download` | `src/main/java/cn/longer233/gamenarrator/export/ExportController.java` |
| GET | `/api/exports/{jobId}` | `src/main/java/cn/longer233/gamenarrator/export/ExportController.java` |
| GET | `/api/knowledge-packs/{code}/export` | `src/main/java/cn/longer233/gamenarrator/event/GameKnowledgePackController.java` |
| GET | `/api/knowledge-packs` | `src/main/java/cn/longer233/gamenarrator/event/GameKnowledgePackController.java` |
| GET | `/api/media-import/browser-auth/config` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| GET | `/api/media-import/download-jobs/{id}` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| GET | `/api/media-import/files/{token}` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| GET | `/api/media-import/preview-jobs/{id}` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| GET | `/api/media-import/previews/{token}` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| GET | `/api/media-import/status` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| GET | `/api/media-import/thumbnails/{token}` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| GET | `/api/tasks/{id}/output` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/tasks/{id}/preview` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/tasks/{id}/render-preview/{index}` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/tasks/{id}/render-preview` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/tasks/{id}/source` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/tasks/{id}` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/tasks/{taskId}/decision-report/export` | `src/main/java/cn/longer233/gamenarrator/community/EditingDecisionReportController.java` |
| GET | `/api/tasks/{taskId}/decision-report` | `src/main/java/cn/longer233/gamenarrator/community/EditingDecisionReportController.java` |
| GET | `/api/tasks/{taskId}/director-reviews` | `src/main/java/cn/longer233/gamenarrator/director/DirectorReviewController.java` |
| GET | `/api/tasks/{taskId}/editor/revisions` | `src/main/java/cn/longer233/gamenarrator/editor/ProjectRevisionController.java` |
| GET | `/api/tasks/{taskId}/editor/waveform` | `src/main/java/cn/longer233/gamenarrator/editor/EditorTimelineController.java` |
| GET | `/api/tasks/{taskId}/editor` | `src/main/java/cn/longer233/gamenarrator/editor/EditorTimelineController.java` |
| GET | `/api/tasks/{taskId}/enhancements` | `src/main/java/cn/longer233/gamenarrator/enhancement/TaskEnhancementController.java` |
| GET | `/api/tasks/{taskId}/events/knowledge-pack` | `src/main/java/cn/longer233/gamenarrator/event/GameEventTimelineController.java` |
| GET | `/api/tasks/{taskId}/events/narrative-plan` | `src/main/java/cn/longer233/gamenarrator/event/GameEventTimelineController.java` |
| GET | `/api/tasks/{taskId}/events` | `src/main/java/cn/longer233/gamenarrator/event/GameEventTimelineController.java` |
| GET | `/api/tasks/{taskId}/exports` | `src/main/java/cn/longer233/gamenarrator/export/ExportController.java` |
| GET | `/api/tasks/{taskId}/quality/narrative-consistency` | `src/main/java/cn/longer233/gamenarrator/quality/NarrativeConsistencyController.java` |
| GET | `/api/tasks/{taskId}/script/reviews` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| GET | `/api/tasks/{taskId}/script` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| GET | `/api/tasks/{taskId}/storyboard/assets` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| GET | `/api/tasks/{taskId}/storyboard/segments/{clipIndex}/thumbnail` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| GET | `/api/tasks/{taskId}/storyboard` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| GET | `/api/tasks/{taskId}/variants` | `src/main/java/cn/longer233/gamenarrator/community/CreativeVariantController.java` |
| GET | `/api/tasks/{taskId}/voice/options` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| GET | `/api/tasks/trash` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/tasks` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| GET | `/api/video-segments/{taskId}/{frameIndex}/thumbnail` | `src/main/java/cn/longer233/gamenarrator/vision/VideoSegmentSearchController.java` |
| GET | `/api/video-segments/{taskId}/clip` | `src/main/java/cn/longer233/gamenarrator/vision/VideoSegmentSearchController.java` |
| GET | `/api/video-segments/search` | `src/main/java/cn/longer233/gamenarrator/vision/VideoSegmentSearchController.java` |
| PATCH | `/api/admin/management/users/{id}` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| PATCH | `/api/assets/{id}/state` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| PATCH | `/api/tasks/{id}/name` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| PATCH | `/api/tasks/{taskId}/editor/revisions/{revisionId}` | `src/main/java/cn/longer233/gamenarrator/editor/ProjectRevisionController.java` |
| POST | `/api/admin/management/cloud-sync/{id}/retry` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| POST | `/api/admin/management/projects/{id}/archive` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| POST | `/api/admin/management/users/{id}/revoke-sessions` | `src/main/java/cn/longer233/gamenarrator/admin/AdminManagementController.java` |
| POST | `/api/admin/storage/cleanup` | `src/main/java/cn/longer233/gamenarrator/storage/StorageAdminController.java` |
| POST | `/api/ai-settings/test` | `src/main/java/cn/longer233/gamenarrator/ai/AiSettingsController.java` |
| POST | `/api/assets/{id}/derive` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| POST | `/api/assets/{id}/download` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| POST | `/api/assets/projects/{taskId}` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| POST | `/api/assets/provider-settings/{provider}/test` | `src/main/java/cn/longer233/gamenarrator/asset/AssetProviderCredentialController.java` |
| POST | `/api/assets/references` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| POST | `/api/assets/repair/bilibili-metadata` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| POST | `/api/assets/upload` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| POST | `/api/assets` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| POST | `/api/auth/anonymous` | `src/main/java/cn/longer233/gamenarrator/identity/AuthController.java` |
| POST | `/api/auth/login` | `src/main/java/cn/longer233/gamenarrator/identity/AuthController.java` |
| POST | `/api/auth/logout` | `src/main/java/cn/longer233/gamenarrator/identity/AuthController.java` |
| POST | `/api/auth/register` | `src/main/java/cn/longer233/gamenarrator/identity/AuthController.java` |
| POST | `/api/cloud-sync/{id}/retry` | `src/main/java/cn/longer233/gamenarrator/cloud/CloudSyncController.java` |
| POST | `/api/community/knowledge-packs/{code}` | `src/main/java/cn/longer233/gamenarrator/community/CommunityResourceController.java` |
| POST | `/api/community/resources/{id}/install` | `src/main/java/cn/longer233/gamenarrator/community/CommunityResourceController.java` |
| POST | `/api/community/styles/{code}` | `src/main/java/cn/longer233/gamenarrator/community/CommunityResourceController.java` |
| POST | `/api/compilations/{id}/items` | `src/main/java/cn/longer233/gamenarrator/compilation/ClipCompilationController.java` |
| POST | `/api/compilations` | `src/main/java/cn/longer233/gamenarrator/compilation/ClipCompilationController.java` |
| POST | `/api/debug/client-events` | `src/main/java/cn/longer233/gamenarrator/diagnostics/DiagnosticsController.java` |
| POST | `/api/effect-presets/import` | `src/main/java/cn/longer233/gamenarrator/effect/EffectController.java` |
| POST | `/api/knowledge-packs` | `src/main/java/cn/longer233/gamenarrator/event/GameKnowledgePackController.java` |
| POST | `/api/media-import/cookies` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| POST | `/api/media-import/download` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| POST | `/api/media-import/download-jobs` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| POST | `/api/media-import/preview` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| POST | `/api/media-import/preview-jobs` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| POST | `/api/media-import/projects` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| POST | `/api/media-import/resolve` | `src/main/java/cn/longer233/gamenarrator/importer/MediaImportController.java` |
| POST | `/api/tasks/{id}/cancel` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| POST | `/api/tasks/{id}/rerender-effects` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| POST | `/api/tasks/{id}/retry` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| POST | `/api/tasks/{id}/start` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| POST | `/api/tasks/{id}/storyboard/approve` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| POST | `/api/tasks/{taskId}/decision-report` | `src/main/java/cn/longer233/gamenarrator/community/EditingDecisionReportController.java` |
| POST | `/api/tasks/{taskId}/director-reviews/{reviewId}/apply` | `src/main/java/cn/longer233/gamenarrator/director/DirectorReviewController.java` |
| POST | `/api/tasks/{taskId}/director-reviews/{reviewId}/decision` | `src/main/java/cn/longer233/gamenarrator/director/DirectorReviewController.java` |
| POST | `/api/tasks/{taskId}/director-reviews` | `src/main/java/cn/longer233/gamenarrator/director/DirectorReviewController.java` |
| POST | `/api/tasks/{taskId}/editor/commands` | `src/main/java/cn/longer233/gamenarrator/editor/EditorTimelineController.java` |
| POST | `/api/tasks/{taskId}/editor/revisions/{revisionId}/checkout` | `src/main/java/cn/longer233/gamenarrator/editor/ProjectRevisionController.java` |
| POST | `/api/tasks/{taskId}/enhancements/assets` | `src/main/java/cn/longer233/gamenarrator/enhancement/TaskEnhancementController.java` |
| POST | `/api/tasks/{taskId}/enhancements/effects` | `src/main/java/cn/longer233/gamenarrator/enhancement/TaskEnhancementController.java` |
| POST | `/api/tasks/{taskId}/enhancements/transcription` | `src/main/java/cn/longer233/gamenarrator/enhancement/TaskEnhancementController.java` |
| POST | `/api/tasks/{taskId}/events/narrative-plan/apply` | `src/main/java/cn/longer233/gamenarrator/event/GameEventTimelineController.java` |
| POST | `/api/tasks/{taskId}/events/narrative-plan/generate` | `src/main/java/cn/longer233/gamenarrator/event/GameEventTimelineController.java` |
| POST | `/api/tasks/{taskId}/events/regenerate-script` | `src/main/java/cn/longer233/gamenarrator/event/GameEventTimelineController.java` |
| POST | `/api/tasks/{taskId}/exports` | `src/main/java/cn/longer233/gamenarrator/export/ExportController.java` |
| POST | `/api/tasks/{taskId}/script/quality-review` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| POST | `/api/tasks/{taskId}/script/segments/{clipIndex}/regenerate` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| POST | `/api/tasks/{taskId}/storyboard/assets/auto` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| POST | `/api/tasks/{taskId}/storyboard/segments/{clipIndex}/assets` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| POST | `/api/tasks/{taskId}/storyboard/segments/{clipIndex}/move` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| POST | `/api/tasks/{taskId}/variants/{variantId}/materialize` | `src/main/java/cn/longer233/gamenarrator/community/CreativeVariantController.java` |
| POST | `/api/tasks/{taskId}/variants/generate` | `src/main/java/cn/longer233/gamenarrator/community/CreativeVariantController.java` |
| POST | `/api/tasks/{taskId}/voice/segments/{clipIndex}/regenerate` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| POST | `/api/tasks/trash/{id}/restore` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| POST | `/api/tasks` | `src/main/java/cn/longer233/gamenarrator/task/web/VideoTaskController.java` |
| POST | `/api/video-segments/search-image` | `src/main/java/cn/longer233/gamenarrator/vision/VideoSegmentSearchController.java` |
| PUT | `/api/ai-settings` | `src/main/java/cn/longer233/gamenarrator/ai/AiSettingsController.java` |
| PUT | `/api/assets/{id}/tags` | `src/main/java/cn/longer233/gamenarrator/asset/AssetCatalogController.java` |
| PUT | `/api/assets/provider-settings` | `src/main/java/cn/longer233/gamenarrator/asset/AssetProviderCredentialController.java` |
| PUT | `/api/compilations/{id}/order` | `src/main/java/cn/longer233/gamenarrator/compilation/ClipCompilationController.java` |
| PUT | `/api/tasks/{taskId}/events/{eventId}` | `src/main/java/cn/longer233/gamenarrator/event/GameEventTimelineController.java` |
| PUT | `/api/tasks/{taskId}/script/segments/{clipIndex}/review` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| PUT | `/api/tasks/{taskId}/script/segments/{clipIndex}` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| PUT | `/api/tasks/{taskId}/storyboard/assets/{placementId}` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |
| PUT | `/api/tasks/{taskId}/storyboard/segments/{clipIndex}` | `src/main/java/cn/longer233/gamenarrator/script/ScriptWorkspaceController.java` |

## 安全与收录说明

- 已收录 JSON、Gradle、properties、CMD、C# 工程、Inno Setup、TOML 等文本格式。
- 单文件默认上限为 262144 bytes；大型词表、生成数据等不进入全文分卷，可用 `-MaxFileBytes` 显式调整。
- 排除 target/build/dist/bin/obj/node_modules、缓存、模型、媒体、Cookie、密钥、local.properties、环境文件及重复 static 构建产物。
- 本工具不修复源文件中已经存在的乱码；乱码应在权威源文件中单独修复，避免导出时猜测替换。
