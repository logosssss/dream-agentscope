/**
 * 登录会话本地存储。仅本机联调使用 localStorage。
 */

const TOKEN_KEY = 'agentscope.loginToken'
const TENANT_KEY = 'agentscope.tenantId'
const USER_KEY = 'agentscope.userId'

/** 当前浏览器里保存的登录态摘要。 */
export interface StoredSession {
  accessToken: string
  tenantId: string
  userId: string
}

/** 读取已保存会话。缺任一字段视为未登录。 */
export function loadSession(): StoredSession | null {
  const accessToken = localStorage.getItem(TOKEN_KEY)
  const tenantId = localStorage.getItem(TENANT_KEY)
  const userId = localStorage.getItem(USER_KEY)
  if (!accessToken || !tenantId || !userId) {
    return null
  }
  return { accessToken, tenantId, userId }
}

/** 登录成功后写入本地。 */
export function saveSession(session: StoredSession): void {
  localStorage.setItem(TOKEN_KEY, session.accessToken)
  localStorage.setItem(TENANT_KEY, session.tenantId)
  localStorage.setItem(USER_KEY, session.userId)
}

/** 退出或 401 时清空。 */
export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TENANT_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 是否已有可用令牌。 */
export function hasSession(): boolean {
  return loadSession() !== null
}
