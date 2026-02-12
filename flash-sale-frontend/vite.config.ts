import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 代理配置：解决跨域问题 (CORS)
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // 转发给 Gateway 端口
        changeOrigin: true,
        // rewrite: (path) => path.replace(/^\/api/, '') // 注意：不要 rewrite，因为我们的 Gateway 路由规则就是匹配 /api/order
      }
    }
  }
})