import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

/**
 * 管理前端单测配置。jsdom 提供 localStorage 等浏览器对象；
 * include 只收 src 下的 *.test.ts，避免把 node_modules 里的测试也拉进来。
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
  },
})
