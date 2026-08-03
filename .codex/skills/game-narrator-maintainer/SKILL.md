---
name: game-narrator-maintainer
description: Inspect, diagnose, modify, test, and document the GameNarrator Spring Boot project. Use for changes involving its video pipeline, task lifecycle, media import, asset catalog, project revisions, Web UI, FFmpeg, yt-dlp, Whisper, Ollama vision/script generation, Piper TTS, Flyway migrations, diagnostics, exports, caching, or DEEPSEEK_PROJECT_CONTEXT.md synchronization.
---

# GameNarrator Maintainer

Maintain the current repository from evidence in its live files. Do not treat bundled notes or prior chat summaries as fresher than source, migrations, configuration, and tests.

## Workflow

1. Locate the repository root from `pom.xml`, then inspect `git status --short` and preserve unrelated user changes.
2. Search with `rg` before opening files. Read only the classes, tests, configuration, migration, and frontend files relevant to the request.
3. For architecture or schema work, read the matching file under `docs/` and current Flyway migrations. Treat migrations as authoritative for persisted schema.
4. For proposed third-party patterns, verify current official documentation or upstream source first. Use similar GitHub/Gitee projects for design comparison, not blind copying. Check license and version compatibility.
5. Implement the smallest coherent change using existing service boundaries and configuration patterns. Avoid hard-coded platforms, browsers, models, storage roots, templates, and machine-specific paths.
6. Validate in proportion to the change. Run focused tests first, then `.\mvnw.cmd test` for backend changes. Run `node --check` on each modified JavaScript file.
7. If tracked project content changed, run `.\scripts\update-deepseek-context.cmd` and verify that `DEEPSEEK_PROJECT_CONTEXT.md` was regenerated without secrets or binary content.
8. Report the behavior changed, validation results, remaining limitations, and clickable file links.

## Guardrails

- Never infer deletion paths from parent traversal. Resolve and validate every target beneath the configured storage root before deletion.
- Do not call a task cancelled unless running Java work and registered external processes actually stop and cannot overwrite terminal state.
- Keep external-process execution bounded: concurrently drain output, enforce timeout, terminate descendants where supported, and preserve interruption state.
- Treat cookies and browser sessions as credentials. Never log, persist in the database, or include their contents in generated context.
- Keep media platform matching, headers, browser priority, network fallback, and cookie domains configuration-driven.
- Preserve transaction ordering: start asynchronous processing only after the creating/updating transaction commits.
- Write JSON through `ObjectMapper`; avoid manual JSON concatenation. Use atomic replacement for important generated manifests.
- Use Flyway for schema changes and add migration tests or repository-level coverage. Never silently rewrite existing production migrations.
- Avoid periodic replacement of interactive DOM. Use incremental updates and preserve playback, form input, focus, expansion, and scroll state.
- Do not claim multi-engine support merely because an interface exists; distinguish adapters from installed working engines.
- Do not add dependencies or change configured model/tool defaults without checking availability and explaining compatibility impact.

## Project Map

Read [references/project-map.md](references/project-map.md) only when locating a subsystem or choosing validation commands. Rebuild conclusions from live files if the map disagrees with the repository.

## Updating This Skill

Update this Skill when stable project conventions, subsystem boundaries, build commands, or safety rules change. Do not copy volatile class lists, model versions, endpoints, test counts, or generated source snapshots into this file. Keep those discoverable from the repository so the Skill remains token-efficient.
