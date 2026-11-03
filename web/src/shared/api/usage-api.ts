import { apiJson } from '@/shared/api/http'
import type { TokenUsageDetail, TokenUsageRow } from '@/shared/types/api.types'

/**
 * 当前用户已经记下的 token 用量。只读，不带提示词，也不改金额。
 */

/** 本人本租户的用量，默认 50 条。 */
export function listUsage(limit = 50): Promise<TokenUsageRow[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<TokenUsageRow[]>(`/api/usage?${q}`)
}

/** 一条用量的记账时间。别人的主键按找不到。 */
export function getUsage(usageId: number): Promise<TokenUsageDetail> {
  return apiJson<TokenUsageDetail>(`/api/usage/${encodeURIComponent(String(usageId))}`)
}
