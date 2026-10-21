import { defineConfig } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';

// https://vitejs.dev/config/
// ⚠️ 代理目标端口必须与后端一致（后端默认 8088，可用 SERVER_PORT 覆盖）
export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8088',
        changeOrigin: true
      }
    }
  }
});
