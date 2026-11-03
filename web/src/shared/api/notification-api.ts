import { apiJson } from '@/shared/api/http'
import type { ApprovalNotice, ApprovalNoticeDetail } from '@/shared/types/api.types'

/**
 * 当前用户的审批通知。只读，不发送，也不改发送状态。
 */

/** 本人的通知，新的在前。表没有租户列。 */
export function listNotifications(limit = 50): Promise<ApprovalNotice[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<ApprovalNotice[]>(`/api/notifications?${q}`)
}

/** 一条通知的通道、状态和创建时间。别人的主键按找不到。 */
export function getNotification(notificationId: number): Promise<ApprovalNoticeDetail> {
  return apiJson<ApprovalNoticeDetail>(`/api/notifications/${encodeURIComponent(String(notificationId))}`)
}
