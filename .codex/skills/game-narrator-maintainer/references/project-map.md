# Project map

Use this as routing guidance, then confirm paths with `rg --files` and symbols with `rg`.

| Area | Primary live sources |
|---|---|
| Build and dependencies | `pom.xml`, Maven wrapper |
| Runtime configuration | `src/main/resources/application.yml` and environment bindings |
| Domain and orchestration | `src/main/java/cn/longer233/gamenarrator/` |
| Database schema | `src/main/resources/db/migration/` |
| Web UI | `src/main/resources/static/` |
| Tests | `src/test/java/` |
| Architecture and requirements | `docs/ARCHITECTURE.md`, `docs/REQUIREMENTS.md`, `docs/DATABASE_DESIGN.md`, `docs/ASSET_LIBRARY_DESIGN.md` |
| Local setup helpers | `scripts/setup-*.ps1` |
| DeepSeek context export | `scripts/export-deepseek-context.ps1`, `scripts/update-deepseek-context.cmd` |

## Common validation

- Backend or configuration: `.\mvnw.cmd test`
- One JavaScript file: `node --check <absolute-file-path>`
- Context synchronization: `.\scripts\update-deepseek-context.cmd`
- Working tree review: `git status --short` and `git diff -- <touched paths>`

## Runtime boundaries

- Spring Boot coordinates task state and persistence.
- FFmpeg handles media extraction, rendering, and export operations.
- yt-dlp handles authorized platform media resolution and download.
- Whisper.cpp provides transcription.
- Ollama-backed models provide vision and script generation according to current configuration.
- Piper is the currently implemented TTS engine behind the voice abstraction unless live source proves otherwise.
- H2/Flyway-backed state and project revisions must remain transactionally consistent.

Never assume tool paths, model names, platform rules, pipeline stage counts, or endpoint lists from this map; read current configuration and source.
