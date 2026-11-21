import { apiJson } from '@/shared/api/http'
import type { DeletionRequestDetail, DeletionRequestSummary } from '@/shared/types/api.types'

/**
 * 当前用户的数据删除请求。
 *
 * 这里只登记与查看，不批准也不删除：批准在管理端，真删由用户进程的执行器异步完成，
 * 进度回写到 status / executedAt / executedNote。
 */

/** 登记一条删除请求。dataType 只能是 SESSION / ATTACHMENT / ALL；reason 可空。 */
export function submitDeletionRequest(
  dataType: string,
  reason: string,
): Promise<DeletionRequestSummary> {
  return apiJson<DeletionRequestSummary>('/api/deletion-requests', {
    method: 'POST',
    body: JSON.stringify({ dataType, reason: reason || null }),
  })
}

/** 本人的删除请求，默认 50 条、最多 100 条。含已决定与已执行的行。 */
export function listDeletionRequests(limit = 50): Promise<DeletionRequestSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<DeletionRequestSummary[]>(`/api/deletion-requests?${q}`)
}

/** 一条删除请求的详情。别人的标识按找不到处理。 */
export function getDeletionRequest(requestId: string): Promise<DeletionRequestDetail> {
  return apiJson<DeletionRequestDetail>(
    `/api/deletion-requests/${encodeURIComponent(requestId)}`,
  )
}
