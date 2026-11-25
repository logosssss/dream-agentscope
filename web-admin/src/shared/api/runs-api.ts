import { apiJson } from '@/shared/api/http'
import type { RunDetail, RunRow } from '@/shared/types/api.types'

/**
 * 管理端运行记录：只读。
 *
 * 运维报障的路径是「用户给出请求号 → 查运行 → 按 runId 看审计事件」，
 * 所以这里只提供列表与按运行号取详情，不提供任何写操作。
 */

/** 近期运行。请求号、用户、会话、状态都可选，传了就 AND 在一起。 */
export function listRuns(
  params: { requestId?: string; userId?: string; sessionId?: string; status?: string; limit?: number } = {},
): Promise<RunRow[]> {
  const q = new URLSearchParams({ limit: String(params.limit ?? 50) })
  if (params.requestId) {
    q.set('requestId', params.requestId)
  }
  if (params.userId) {
    q.set('userId', params.userId)
  }
  if (params.sessionId) {
    q.set('sessionId', params.sessionId)
  }
  if (params.status) {
    q.set('status', params.status)
  }
  return apiJson<RunRow[]>(`/api/admin/runs?${q}`)
}

/** 一轮运行的详情，带步骤。找不到时后端回 404。 */
export function getRun(runId: string): Promise<RunDetail> {
  return apiJson<RunDetail>(`/api/admin/runs/${encodeURIComponent(runId)}`)
}
