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
  /** 调用方令牌，对应后端 AGENTSCOPE_CALLER_TOKEN */
  readonly VITE_CALLER_TOKEN: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
