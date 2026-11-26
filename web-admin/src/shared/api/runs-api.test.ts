import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiJson = vi.fn()

vi.mock('@/shared/api/http', () => ({
  apiJson: (...args: unknown[]) => apiJson(...args),
}))

import { listRuns } from './runs-api'

/**
 * 运行列表的查询串。请求号要原样交给后端，空值不要变成匹配空串。
 */
describe('listRuns', () => {
  beforeEach(() => {
    apiJson.mockReset()
    apiJson.mockResolvedValue([])
  })

  it('把请求号放进查询串', async () => {
    await listRuns({ requestId: 'req-1', limit: 20 })

    expect(apiJson).toHaveBeenCalledWith('/api/admin/runs?limit=20&requestId=req-1')
  })

  it('没填请求号时不带这个条件', async () => {
    await listRuns({})

    expect(apiJson).toHaveBeenCalledWith('/api/admin/runs?limit=50')
  })

  it('租户和用户一起放进查询串', async () => {
    await listRuns({ tenantId: 'demo', userId: 'u-1' })

    expect(apiJson).toHaveBeenCalledWith('/api/admin/runs?limit=50&tenantId=demo&userId=u-1')
  })
})
