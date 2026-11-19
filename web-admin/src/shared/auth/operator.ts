/**
 * 操作人本地记忆。对应请求头 X-Operator-Id；空则后端默认 admin。
 */

const KEY = 'agentscope.admin.operatorId'

/** 读取已记住的操作人。没有则空串。 */
export function loadOperatorId(): string {
  try {
    return localStorage.getItem(KEY)?.trim() || ''
  } catch {
    return ''
  }
}

/** 写入操作人。空白则删键。 */
export function saveOperatorId(value: string): void {
  const trimmed = value.trim()
  try {
    if (!trimmed) {
      localStorage.removeItem(KEY)
      return
    }
    localStorage.setItem(KEY, trimmed)
  } catch {
    // 隐私模式等写失败不挡页面
  }
}

/**
 * 本地联调看 VITE_ADMIN_TOKEN；现网网关模式（VITE_ADMIN_GATEWAY=true）由 Nginx 代填令牌，
 * 浏览器包内不放密钥，仍视为已就绪。
 */
export function hasAdminToken(): boolean {
  if (import.meta.env.VITE_ADMIN_GATEWAY === 'true') {
    return true
  }
  const token = import.meta.env.VITE_ADMIN_TOKEN
  return !!token && token.trim() !== ''
}

/** 是否走网关代填（生产静态站）。 */
export function isAdminGateway(): boolean {
  return import.meta.env.VITE_ADMIN_GATEWAY === 'true'
}
