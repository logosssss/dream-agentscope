import { describe, expect, it } from 'vitest'
import { parseReadiness } from './readiness'

/**
 * 就绪正文是平铺的键，不是 ApiResult。503 也要能拆出失败项。
 */
describe('parseReadiness', () => {
  it('200 时列出各项', () => {
    const parsed = parseReadiness({ status: 'UP', mysql: 'UP', redis: 'UP', milvus: 'UP' }, true)

    expect(parsed.status).toBe('UP')
    expect(parsed.checks).toEqual([
      { name: 'mysql', state: 'UP' },
      { name: 'redis', state: 'UP' },
      { name: 'milvus', state: 'UP' },
    ])
  })

  it('503 仍标出 DOWN', () => {
    const parsed = parseReadiness(
      { status: 'DOWN', mysql: 'DOWN', redis: 'UP', milvus: 'SKIPPED' },
      false,
    )

    expect(parsed.status).toBe('DOWN')
    expect(parsed.checks.find((item) => item.name === 'mysql')?.state).toBe('DOWN')
  })
})
