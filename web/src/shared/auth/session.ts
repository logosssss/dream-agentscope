/**
 * 登录会话本地存储。仅本机联调使用 localStorage。
 */

const TOKEN_KEY = 'agentscope.loginToken'
const TENANT_KEY = 'agentscope.tenantId'
const USER_KEY = 'agentscope.userId'
const NAME_KEY = 'agentscope.displayName'

/** 当前浏览器里保存的登录态摘要。 */
export interface StoredSession {
  accessToken: string
  tenantId: string
  userId: string
  displayName: string
}

/** 读取已保存会话。缺令牌、租户或用户视为未登录。显示名可以空。 */
export function loadSession(): StoredSession | null {
  const accessToken = localStorage.getItem(TOKEN_KEY)
  const tenantId = localStorage.getItem(TENANT_KEY)
  const userId = localStorage.getItem(USER_KEY)
  if (!accessToken || !tenantId || !userId) {
    return null
  }
  return {
    accessToken,
    tenantId,
    userId,
    displayName: localStorage.getItem(NAME_KEY) || '',
  }
}

/** 登录成功后写入本地。 */
export function saveSession(session: StoredSession): void {
  localStorage.setItem(TOKEN_KEY, session.accessToken)
  localStorage.setItem(TENANT_KEY, session.tenantId)
  localStorage.setItem(USER_KEY, session.userId)
  localStorage.setItem(NAME_KEY, session.displayName || '')
}

/** 退出或 401 时清空。 */
export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TENANT_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(NAME_KEY)
}

/** 是否已有可用令牌。 */
export function hasSession(): boolean {
  return loadSession() !== null
}
