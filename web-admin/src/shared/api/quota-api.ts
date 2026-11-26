import { apiJson } from '@/shared/api/http'
import type { QuotaKind, QuotaUsageRow, TenantQuotaSummary } from '@/shared/types/api.types'

/**
 * 四类租户额度目录。不带回已用量 / 当前占位。
 */

const PATHS: Record<QuotaKind, string> = {
  requests: '/api/admin/tenant-quotas',
  tokens: '/api/admin/tenant-token-quotas',
  cost: '/api/admin/tenant-cost-quotas',
  concurrent: '/api/admin/tenant-concurrent-quotas',
}

/** 最近登记的某类额度。 */
export function listQuotas(kind: QuotaKind, limit = 50): Promise<TenantQuotaSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<TenantQuotaSummary[]>(`${PATHS[kind]}?${q}`)
}

/** 新登记一行。同租户同类再登一次冲突。 */
export function registerQuota(
  kind: QuotaKind,
  tenantId: string,
  limit: number,
): Promise<TenantQuotaSummary> {
  return apiJson<TenantQuotaSummary>(PATHS[kind], {
    method: 'POST',
    body: JSON.stringify({ tenantId, limit }),
  })
}

/**
 * 某个租户最近的额度流水。空白租户不发请求。
 * 返回的是已通过的流水，不是 Redis 里的剩余额度。
 */
export function listQuotaUsage(tenantId: string, limit = 50): Promise<QuotaUsageRow[]> {
  const id = tenantId.trim()
  if (!id) {
    return Promise.resolve([])
  }
  const q = new URLSearchParams({ tenantId: id, limit: String(limit) })
  return apiJson<QuotaUsageRow[]>(`/api/admin/quota-usage?${q}`)
}

/** 改已有上限。没有行返回找不到。 */
export function updateQuota(
  kind: QuotaKind,
  tenantId: string,
  limit: number,
): Promise<TenantQuotaSummary> {
  return apiJson<TenantQuotaSummary>(
    `${PATHS[kind]}/${encodeURIComponent(tenantId)}`,
    {
      method: 'POST',
      body: JSON.stringify({ tenantId, limit }),
    },
  )
}
