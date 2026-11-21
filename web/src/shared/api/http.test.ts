import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiBusinessError, apiJson, authHeaders, isGatewayMode, withRequestId } from './http'

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

  it('每条请求都带请求号，且不覆盖调用方自己传的号', () => {
    vi.stubEnv('VITE_GATEWAY', 'true')

    const first = authHeaders().get('X-Request-Id')
    const second = authHeaders().get('X-Request-Id')
    expect(first).toBeTruthy()
    expect(first).not.toBe(second)

    const kept = authHeaders({ 'X-Request-Id': 'mine' }).get('X-Request-Id')
    expect(kept).toBe('mine')
  })
})

/**
 * 报错里要能让用户把请求号贴给运维。
 * 后端回显 X-Request-Id 时拼在文案末尾；没回显（直连别的服务、老代理）就保持原文。
 */
describe('错误文案带请求号', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  function stubFetch(body: unknown, status: number, headers: Record<string, string>) {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify(body), { status, headers })),
    )
  }

  it('withRequestId 拼接与降级', () => {
    const withHeader = new Response('{}', { headers: { 'X-Request-Id': 'req-abc' } })
    expect(withRequestId('失败了', withHeader)).toBe('失败了（请求号 req-abc）')

    const blank = new Response('{}', { headers: { 'X-Request-Id': '   ' } })
    expect(withRequestId('失败了', blank)).toBe('失败了')
  })

  it('业务失败（HTTP 200 + ok=false）带上请求号', async () => {
    vi.stubEnv('VITE_GATEWAY', 'true')
    stubFetch({ ok: false, code: 'CONFLICT', message: '这次请求已经提交过' }, 200, {
      'X-Request-Id': 'req-biz',
    })

    await expect(apiJson('/api/chat')).rejects.toThrow(/这次请求已经提交过（请求号 req-biz）/)
    await expect(apiJson('/api/chat')).rejects.toBeInstanceOf(ApiBusinessError)
  })

  it('503 与非 JSON 响应也带请求号', async () => {
    vi.stubEnv('VITE_GATEWAY', 'true')
    stubFetch({ ok: false, message: '维护中' }, 503, { 'X-Request-Id': 'req-503' })
    await expect(apiJson('/api/chat')).rejects.toThrow(/维护中（请求号 req-503）/)

    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('<html>bad gateway</html>', { status: 502, headers: { 'X-Request-Id': 'req-502' } })),
    )
    await expect(apiJson('/api/chat')).rejects.toThrow(/响应不是 JSON（HTTP 502）（请求号 req-502）/)
  })

  it('没有回显头时文案不变', async () => {
    vi.stubEnv('VITE_GATEWAY', 'true')
    stubFetch({ ok: false, code: 'NOT_FOUND', message: '找不到' }, 404, {})

    await expect(apiJson('/api/chat')).rejects.toThrow(/^找不到$/)
  })
})
