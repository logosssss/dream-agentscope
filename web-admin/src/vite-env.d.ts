/// <reference types="vite/client" />

/**
 * 构建期注入的环境变量。密钥只放本机 .env.local，不要提交。
 */
interface ImportMetaEnv {
  /** 管理进程调用方令牌，对应 AGENTSCOPE_ADMIN_TOKEN */
  readonly VITE_ADMIN_TOKEN: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
