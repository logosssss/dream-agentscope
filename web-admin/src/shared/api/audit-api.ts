import { apiJson } from '@/shared/api/http'
import type { OperationAuditSummary, SafetyInterventionSummary } from '@/shared/types/api.types'

/**
 * 安全干预与后台操作审计只读列表。
 */

/** 最近安全干预。 */
export function listSafetyInterventions(limit = 50): Promise<SafetyInterventionSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<SafetyInterventionSummary[]>(`/api/admin/safety-interventions?${q}`)
}

/** 最近后台操作审计。 */
export function listOperationAudits(limit = 50): Promise<OperationAuditSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<OperationAuditSummary[]>(`/api/admin/operation-audits?${q}`)
}
