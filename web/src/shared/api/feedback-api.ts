import { apiJson } from '@/shared/api/http'
import type { FeedbackDetail, FeedbackSummary } from '@/shared/types/api.types'

/**
 * 当前用户对一次运行的评分。不接收文件，也不核对运行是否存在。
 */

/** 提交一条评分。评分为 1 到 5。运行和说明可以空。 */
export function createFeedback(
  rating: number,
  runId: string,
  comment: string,
): Promise<void> {
  return apiJson<void>('/api/feedback', {
    method: 'POST',
    body: JSON.stringify({
      rating,
      runId: runId || null,
      comment: comment || null,
    }),
  })
}

/** 本人的反馈，默认 50 条。表没有租户列。 */
export function listFeedback(limit = 50): Promise<FeedbackSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<FeedbackSummary[]>(`/api/feedback?${q}`)
}

/** 一条反馈的创建时间。别人的主键按找不到。 */
export function getFeedback(feedbackId: number): Promise<FeedbackDetail> {
  return apiJson<FeedbackDetail>(`/api/feedback/${encodeURIComponent(String(feedbackId))}`)
}
