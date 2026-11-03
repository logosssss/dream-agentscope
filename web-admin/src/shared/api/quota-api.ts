import { apiJson } from '@/shared/api/http'
import type { QuotaKind, TenantQuotaSummary } from '@/shared/types/api.types'

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
