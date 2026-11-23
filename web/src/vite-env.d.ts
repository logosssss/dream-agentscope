/// <reference types="vite/client" />

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 登录页等无需会话 */
    public?: boolean
    /** 写入 --color-brand-primary */
    themeColor?: string
    /** 与后端 agentId 对齐 */
    agentId?: string
  }
}

/**
 * 构建期注入的环境变量。密钥只放本机 .env.local，不要提交。
 */
interface ImportMetaEnv {
  /** 调用方令牌，对应后端 AGENTSCOPE_CALLER_TOKEN；只在本地直连时使用 */
  readonly VITE_CALLER_TOKEN: string
  /**
   * 网关模式：现网静态构建设为 'true'，X-Caller-Token 由 Nginx 注入。
   * 与部署脚本的 Gate 2 配套——生产构建禁止带 VITE_CALLER_TOKEN。
   */
  readonly VITE_GATEWAY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
