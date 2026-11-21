import { apiJson } from '@/shared/api/http'
import type { DeletionRequestSummary } from '@/shared/types/api.types'

/**
 * 数据删除请求：列出近期请求、把待处理改成 APPROVED / REJECTED。
 * 批准不删数据，真删在用户进程异步执行，结果看 executedAt / executedNote。
 */

/** 近期请求。status 为空或 ALL 表示不过滤。 */
export function listDeletionRequests(
  status: string,
  limit = 50,
): Promise<DeletionRequestSummary[]> {
  const query = new URLSearchParams({ limit: String(limit) })
  if (status && status !== 'ALL') {
    query.set('status', status)
  }
  return apiJson<DeletionRequestSummary[]>(`/api/admin/deletion-requests?${query.toString()}`)
}

/** 把待处理改成 APPROVED 或 REJECTED。 */
export function decideDeletionRequest(
  requestId: string,
  decision: 'APPROVED' | 'REJECTED',
): Promise<DeletionRequestSummary> {
  return apiJson<DeletionRequestSummary>(
    `/api/admin/deletion-requests/${encodeURIComponent(requestId)}/decision`,
    {
      method: 'POST',
      body: JSON.stringify({ decision }),
    },
  )
}
