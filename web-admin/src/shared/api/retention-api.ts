import { apiJson } from '@/shared/api/http'
import type { RetentionPolicySummary } from '@/shared/types/api.types'

/**
 * 数据保留策略目录。只登记、停用、恢复与列表，不触发清理。
 */

/** 最近保留策略，含停用。 */
export function listRetentionPolicies(limit = 50): Promise<RetentionPolicySummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<RetentionPolicySummary[]>(`/api/admin/retention-policies?${q}`)
}

/**
 * 登记一条。空白范围按全局 `*`；空白动作按 ARCHIVE。
 * 同一数据类型+范围冲突由服务端返回。
 */
export function registerRetentionPolicy(body: {
  dataType: string
  scope: string
  retainDays: number
  action: string
}): Promise<RetentionPolicySummary> {
  return apiJson<RetentionPolicySummary>('/api/admin/retention-policies', {
    method: 'POST',
    body: JSON.stringify({
      dataType: body.dataType,
      scope: body.scope || null,
      retainDays: body.retainDays,
      action: body.action || null,
    }),
  })
}

/** 空白范围按全局。 */
function scopeBody(scope: string) {
  return { scope: scope || null }
}

/** 停用仍启用的策略。 */
export function disableRetentionPolicy(
  dataType: string,
  scope: string,
): Promise<RetentionPolicySummary> {
  return apiJson<RetentionPolicySummary>(
    `/api/admin/retention-policies/${encodeURIComponent(dataType)}/disable`,
    {
      method: 'POST',
      body: JSON.stringify(scopeBody(scope)),
    },
  )
}

/** 恢复已停用的策略。 */
export function restoreRetentionPolicy(
  dataType: string,
  scope: string,
): Promise<RetentionPolicySummary> {
  return apiJson<RetentionPolicySummary>(
    `/api/admin/retention-policies/${encodeURIComponent(dataType)}/restore`,
    {
      method: 'POST',
      body: JSON.stringify(scopeBody(scope)),
    },
  )
}
