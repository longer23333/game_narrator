import {defineConfig, loadEnv} from 'vite';
import {resolve} from 'node:path';

export default defineConfig(({mode}) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backend = env.VITE_BACKEND_URL || 'http://localhost:8082';
  return {
    server: {
      host: '127.0.0.1',
      port: Number(env.VITE_DEV_PORT || 5173),
      strictPort: true,
      proxy: {
        '/api': {target: backend, changeOrigin: true}
      }
    },
    preview: {
      host: '127.0.0.1',
      port: Number(env.VITE_PREVIEW_PORT || 4173),
      strictPort: true
    },
    build: {
      outDir: resolve(__dirname, '../src/main/resources/static'),
      emptyOutDir: true
    }
  };
});
