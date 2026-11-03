import { apiJson } from '@/shared/api/http'
import type { ApprovalRow } from '@/shared/types/api.types'

/**
 * 管理端待审批与决定。通道由后端固定为 CONSOLE。
 */

/** 待处理审批列表。 */
export function listPendingApprovals(limit = 50): Promise<ApprovalRow[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<ApprovalRow[]>(`/api/admin/approvals?${q}`)
}

/**
 * 批准或拒绝。approver 建议用顶栏操作人；空时仍提交空串，后端可落默认。
 */
export function decideApproval(
  approvalId: string,
  decision: 'APPROVED' | 'REJECTED',
  approver: string,
  reason = '',
): Promise<ApprovalRow> {
  return apiJson<ApprovalRow>(
    `/api/admin/approvals/${encodeURIComponent(approvalId)}/decision`,
    {
      method: 'POST',
      body: JSON.stringify({
        decision,
        approver: approver || null,
        reason: reason || null,
      }),
    },
  )
}
