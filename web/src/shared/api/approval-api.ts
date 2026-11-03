import { apiJson } from '@/shared/api/http'
import type { ApprovalDecisionView, ApprovalDetail, PendingApproval } from '@/shared/types/api.types'

/**
 * 用户端待确认与自批。通道由后端固定为 WEB，不执行工具。
 */

/** 本人本租户仍待确认的单据。 */
export function listPendingApprovals(limit = 50): Promise<PendingApproval[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<PendingApproval[]>(`/api/approvals/pending?${q}`)
}

/** 本人本租户的一张审批。不改决定，也不执行工具。 */
export function getApproval(approvalId: string): Promise<ApprovalDetail> {
  return apiJson<ApprovalDetail>(`/api/approvals/${encodeURIComponent(approvalId)}`)
}

/**
 * 批准或拒绝。审批人取当前登录用户，请求体不传 approver。
 */
export function decideApproval(
  approvalId: string,
  decision: 'APPROVED' | 'REJECTED',
  reason = '',
): Promise<ApprovalDecisionView> {
  return apiJson<ApprovalDecisionView>(
    `/api/approvals/${encodeURIComponent(approvalId)}/decision`,
    {
      method: 'POST',
      body: JSON.stringify({
        decision,
        reason: reason || null,
      }),
    },
  )
}
