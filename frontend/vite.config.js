import {defineConfig, loadEnv} from 'vite';
import vue from '@vitejs/plugin-vue';
import {resolve} from 'node:path';

export default defineConfig(({mode}) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backend = env.VITE_BACKEND_URL || 'http://127.0.0.1:8081';
  return {
    plugins: [vue()],
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
      emptyOutDir: true,
      rollupOptions: {
        input: {
          index: resolve(__dirname, 'index.html'),
          'memphis-motion': resolve(__dirname, 'src/memphis-motion.js')
        },
        output: {
          entryFileNames: assetInfo => assetInfo.name === 'memphis-motion'
            ? 'assets/memphis-motion.js'
            : 'assets/[name]-[hash].js',
          assetFileNames: assetInfo => assetInfo.names?.some(name => name.endsWith('.css'))
            ? 'assets/memphis-theme.css'
            : 'assets/[name]-[hash][extname]'
        }
      }
    }
  };
});
