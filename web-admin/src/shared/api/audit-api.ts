import { apiJson } from '@/shared/api/http'
import type { AuditEventRow, OperationAuditSummary, SafetyInterventionSummary } from '@/shared/types/api.types'

/**
 * 安全干预、后台操作审计与执行事件审计的只读列表。
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

/**
 * 执行内核落的事件审计。三个条件都可选，传了就 AND 在一起。
 * 运维报障时一般先拿请求号查运行记录，再用这里的 runId 看那一轮的中间步骤。
 */
export function listAuditEvents(params: {
  runId?: string
  sessionId?: string
  userId?: string
  limit?: number
} = {}): Promise<AuditEventRow[]> {
  const q = new URLSearchParams({ limit: String(params.limit ?? 50) })
  if (params.runId) {
    q.set('runId', params.runId)
  }
  if (params.sessionId) {
    q.set('sessionId', params.sessionId)
  }
  if (params.userId) {
    q.set('userId', params.userId)
  }
  return apiJson<AuditEventRow[]>(`/api/admin/audits?${q}`)
}
