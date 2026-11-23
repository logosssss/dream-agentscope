import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminHeaders } from './http'

/**
 * 管理端鉴权头组装。
 * 关键约束：现网是网关代填 X-Caller-Token，浏览器包内不放 Admin Token；
 * 所以「网关模式不带头」和「本地缺令牌要报可操作的错」都必须锁住。
 */
describe('adminHeaders', () => {
  beforeEach(() => {
    vi.unstubAllEnvs()
    localStorage.clear()
  })

  it('网关模式不设置 X-Caller-Token', () => {
    vi.stubEnv('VITE_ADMIN_GATEWAY', 'true')
    vi.stubEnv('VITE_ADMIN_TOKEN', '')

    const headers = adminHeaders()

    expect(headers.has('X-Caller-Token')).toBe(false)
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('本地模式带上去掉首尾空白的令牌', () => {
    vi.stubEnv('VITE_ADMIN_GATEWAY', 'false')
    vi.stubEnv('VITE_ADMIN_TOKEN', '  local-admin-token  ')

    expect(adminHeaders().get('X-Caller-Token')).toBe('local-admin-token')
  })

  it('本地模式缺令牌时报可操作的错', () => {
    vi.stubEnv('VITE_ADMIN_GATEWAY', 'false')
    vi.stubEnv('VITE_ADMIN_TOKEN', '')

    expect(() => adminHeaders()).toThrow(/未配置 VITE_ADMIN_TOKEN/)
  })
})
