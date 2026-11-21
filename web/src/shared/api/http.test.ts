import { afterEach, describe, expect, it, vi } from 'vitest'
import { authHeaders, isGatewayMode } from './http'

/**
 * 回归用例的来源：现网构建禁止把 VITE_CALLER_TOKEN 打进前端包，
 * 但旧实现没令牌就抛错，导致生产包整站不可用。
 * 这三条锁住"网关模式不带令牌 / 本地模式带令牌 / 两边都没有就报错"。
 */
describe('authHeaders', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    localStorage.clear()
  })

  it('网关模式下不带 X-Caller-Token', () => {
    vi.stubEnv('VITE_GATEWAY', 'true')
    vi.stubEnv('VITE_CALLER_TOKEN', '')

    expect(isGatewayMode()).toBe(true)
    const headers = authHeaders()
    expect(headers.has('X-Caller-Token')).toBe(false)
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('本地模式带上令牌', () => {
    vi.stubEnv('VITE_GATEWAY', 'false')
    vi.stubEnv('VITE_CALLER_TOKEN', '  local-token  ')

    expect(isGatewayMode()).toBe(false)
    expect(authHeaders().get('X-Caller-Token')).toBe('local-token')
  })

  it('既不是网关模式又没有令牌时给出可执行的报错', () => {
    vi.stubEnv('VITE_GATEWAY', 'false')
    vi.stubEnv('VITE_CALLER_TOKEN', '   ')

    expect(() => authHeaders()).toThrow(/VITE_GATEWAY=true/)
  })
})
