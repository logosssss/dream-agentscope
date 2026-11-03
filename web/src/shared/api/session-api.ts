import { apiJson } from '@/shared/api/http'
import type { ChatMessageItem, SessionSummary } from '@/shared/types/api.types'

/**
 * 会话列表与消息历史。身份靠 Bearer，不传租户用户。
 */

/** 当前用户活动会话，按最近活动倒序。 */
export function listSessions(): Promise<SessionSummary[]> {
  return apiJson<SessionSummary[]>('/api/sessions')
}

/**
 * 一次拉齐本人会话气泡。limit 默认跟接入层 100；服务端最多 200。
 */
export function listMessages(sessionId: string, limit = 100): Promise<ChatMessageItem[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<ChatMessageItem[]>(
    `/api/sessions/${encodeURIComponent(sessionId)}/messages?${q}`,
  )
}
