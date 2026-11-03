import { apiJson } from '@/shared/api/http'
import type { DeletionRequestSummary } from '@/shared/types/api.types'

/**
 * 数据删除请求决定。管理端无列表；批准后仍不执行删除。
 */

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
