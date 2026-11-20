import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

/**
 * 管理前端开发构建。/api 与 /health 代理到管理进程 8100。
 * 现网静态发布用 VITE_ADMIN_BASE=/ops/，与 Nginx location 对齐。
 */
export default defineConfig({
  base: process.env.VITE_ADMIN_BASE || '/',
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8100',
        changeOrigin: true,
      },
      '/health': {
        target: 'http://127.0.0.1:8100',
        changeOrigin: true,
      },
    },
  },
})
