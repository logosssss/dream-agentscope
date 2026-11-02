import { apiJson } from '@/shared/api/http'
import type { LoginResponse } from '@/shared/types/api.types'

/**
 * 登录与退出接口。仍需 X-Caller-Token。
 */

/** 无密码登录，签发 Redis 会话令牌。 */
export function login(tenantId: string, userId: string): Promise<LoginResponse> {
  return apiJson<LoginResponse>('/api/login', {
    method: 'POST',
    body: JSON.stringify({ tenantId, userId }),
  })
}

/** 删除服务端会话。失败时调用方仍应清本地。 */
export function logout(accessToken: string): Promise<null> {
  return apiJson<null>('/api/logout', {
    method: 'POST',
    body: JSON.stringify({ accessToken }),
  })
}
