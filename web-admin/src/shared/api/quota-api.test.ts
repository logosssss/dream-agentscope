import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiJson = vi.fn()

vi.mock('@/shared/api/http', () => ({
  apiJson: (...args: unknown[]) => apiJson(...args),
}))

import { listQuotaUsage } from './quota-api'

/**
 * 额度流水必须带租户。没选租户时不能打到后端。
 */
describe('listQuotaUsage', () => {
  beforeEach(() => {
    apiJson.mockReset()
    apiJson.mockResolvedValue([])
  })

  it('把租户放进查询串', async () => {
    await listQuotaUsage(' demo ')

    expect(apiJson).toHaveBeenCalledWith('/api/admin/quota-usage?tenantId=demo&limit=50')
  })

  it('空白租户不发请求', async () => {
    await expect(listQuotaUsage('  ')).resolves.toEqual([])

    expect(apiJson).not.toHaveBeenCalled()
  })
})
