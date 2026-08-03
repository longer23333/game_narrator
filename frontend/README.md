# GameNarrator Frontend

Vite development frontend for the GameNarrator Spring Boot API.

```powershell
cd frontend
npm install
npm run dev
```

The development server uses `http://localhost:5173` and proxies `/api` to
`VITE_BACKEND_URL` (default `http://localhost:8082`).

`npm run build` writes the production bundle to
`../src/main/resources/static`, so Spring Boot can still ship the application
as one deployable artifact.
