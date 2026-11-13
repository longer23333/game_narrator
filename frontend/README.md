# GameNarrator Frontend

Vite development frontend for the GameNarrator Spring Boot API.

```powershell
cd frontend
npm install
npm run dev
```

The development server prefers `http://127.0.0.1:5173` and automatically tries
the next available port if it is occupied. Open the Local URL printed by Vite.
Set `VITE_DEV_PORT` to choose a different preferred port, or pass `-- --strictPort`
to fail on conflicts. The combined `scripts/start-development.ps1` launcher uses
the fixed port 5173 for its readiness checks.
Requests to `/api` are proxied to `VITE_BACKEND_URL` (default `http://127.0.0.1:8081`).

`npm run build` writes the production bundle to
`../src/main/resources/static`, so Spring Boot can still ship the application
as one deployable artifact.

Two things surprise people about that build:

- It **empties `static/assets/`**. The entry bundle is emitted as
  `index-<contenthash>.js`, so every build produces a new filename and deletes the
  previous one. A `D` entry in `git status` is expected, not a mistake.
- `frontend/index.html` is the template; the build rewrites
  `src/main/resources/static/index.html` from it. Both files are tracked and must be
  edited together when you touch the `<head>` (the `preload` hint lives there).

Caching then splits by filename: hashed bundles under `/assets/` are served
`immutable` for a year, everything else stays `no-cache`. See
[开发与运维](../docs/DEVELOPMENT_OPERATIONS.md) for the full table and the
"changed it but nothing happened" checklist.

The primary flow is upload, generate a playable preview, then edit/export.
New uploads use the filename when no title is supplied and use a neutral brief
when instructions are blank. Storyboard review is opt-in; it can still pause
generation when selected. Existing tasks retain their review settings.
The editor loads its six core resources first. Advanced planning, director
reviews, knowledge packs and community tools load only when expanded, and a
failure there leaves the basic editor usable. Production diagnostics and
enhancement controls remain available under the task's advanced details.
Online supplemental-asset discovery is opt-in so unavailable community sources
do not delay the normal upload-to-preview flow.
