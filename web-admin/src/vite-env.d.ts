/// <reference types="vite/client" />

/**
 * 构建期注入的环境变量。密钥只放本机 .env.local，不要提交。
 */
interface ImportMetaEnv {
  /** 管理进程调用方令牌，对应 AGENTSCOPE_ADMIN_TOKEN */
  readonly VITE_ADMIN_TOKEN: string
  /** 现网构建为 true：令牌由 Nginx 代填，浏览器包内不放密钥。 */
  readonly VITE_ADMIN_GATEWAY: string
  readonly BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
