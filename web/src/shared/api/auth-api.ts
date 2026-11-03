import { apiJson } from '@/shared/api/http'
import type { LoginResponse } from '@/shared/types/api.types'

/**
 * 客户端注册、密码登录和微信扫码。这几步不附带旧会话。
 */

/** 注册或登录成功后的会话。 */
function authBody(accountName: string, password: string): RequestInit {
  return {
    method: 'POST',
    body: JSON.stringify({ accountName, password }),
  }
}

/** 密码注册。成功时已经登录。 */
export function register(accountName: string, password: string): Promise<LoginResponse> {
  return apiJson<LoginResponse>('/api/register', authBody(accountName, password), { bearer: false })
}

/** 密码登录。失败文案由服务端给出。 */
export function login(accountName: string, password: string): Promise<LoginResponse> {
  return apiJson<LoginResponse>('/api/login', authBody(accountName, password), { bearer: false })
}

/** 扫码开关。没配密钥时为 false。 */
export function authConfig(): Promise<{ wxScanLoginEnabled: boolean }> {
  return apiJson('/api/auth/config', undefined, { bearer: false })
}

/** 网站申请一张小程序码。 */
export function createWxTicket(): Promise<{ ticketId: string; expiresIn: number; qrcodeDataUrl: string }> {
  return apiJson('/api/auth/wx/ticket', { method: 'POST' }, { bearer: false })
}

/** 查询票。确认后 login 里是会话。 */
export function pollWxTicket(
  ticketId: string,
): Promise<{ status: string; login: LoginResponse | null }> {
  return apiJson(`/api/auth/wx/ticket/${encodeURIComponent(ticketId)}`, undefined, { bearer: false })
}

/** 删除服务端会话。失败时调用方仍应清本地。 */
export function logout(accessToken: string): Promise<null> {
  return apiJson<null>('/api/logout', {
    method: 'POST',
    body: JSON.stringify({ accessToken }),
  })
}
