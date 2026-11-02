import { authHeaders } from '@/shared/api/http'
import { clearSession } from '@/shared/auth/session'
import type { AgentStreamEvent, ChatStreamRequest } from '@/shared/types/api.types'

/**
 * SSE 流式消费。只把 text_block_delta 交给业务追加正文。
 */

export interface StreamHandlers {
  /** 文本增量 */
  onDelta: (text: string) => void
  /** 非正文事件的轻提示，例如等待确认 */
  onStatus?: (message: string) => void
  /** 流正常或中止结束后回调 */
  onDone?: () => void
}

/**
 * 发起 /api/chat/stream。返回 abort 函数供「停止生成」。
 */
export function useStreaming() {
  /**
   * 解析 SSE：按行缓冲，遇到 data: 后尝试 JSON。
   * 流结束以 reader 完成或 abort 为准，不依赖 [DONE]。
   */
  async function startStream(
    payload: ChatStreamRequest,
    handlers: StreamHandlers,
    signal?: AbortSignal,
  ): Promise<void> {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(payload),
      signal,
    })

    if (response.status === 401) {
      clearSession()
      throw new Error('未授权或登录已失效，请重新登录')
    }

    if (response.status === 503) {
      throw new Error('服务暂不可用（503）')
    }

    if (!response.ok) {
      const raw = await response.text()
      throw new Error(parseErrorMessage(raw, response.status))
    }

    if (!response.body) {
      throw new Error('响应没有可读流')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }
        buffer += decoder.decode(value, { stream: true })
        buffer = consumeSseBuffer(buffer, handlers)
      }
      if (buffer.trim()) {
        consumeSseBuffer(buffer + '\n\n', handlers)
      }
    } finally {
      handlers.onDone?.()
    }
  }

  return { startStream }
}

/** 从可能的 ApiResult 文本里取出可读错误。 */
function parseErrorMessage(raw: string, status: number): string {
  if (!raw) {
    return `请求失败（HTTP ${status}）`
  }
  try {
    const body = JSON.parse(raw) as { message?: string; code?: string; ok?: boolean }
    if (body.message) {
      return body.message
    }
    if (body.code) {
      return body.code
    }
  } catch {
    // 非 JSON 时退回原文截断
  }
  return raw.length > 200 ? `${raw.slice(0, 200)}…` : raw
}

/**
 * 消费完整 SSE 事件块，返回未拼完的残留。
 */
function consumeSseBuffer(buffer: string, handlers: StreamHandlers): string {
  const parts = buffer.split(/\r?\n\r?\n/)
  const rest = parts.pop() ?? ''
  for (const block of parts) {
    dispatchSseBlock(block, handlers)
  }
  return rest
}

/** 处理单个 SSE 事件块中的 data 行。 */
function dispatchSseBlock(block: string, handlers: StreamHandlers): void {
  const lines = block.split(/\r?\n/)
  for (const line of lines) {
    if (!line.startsWith('data:')) {
      continue
    }
    const data = line.slice(5).trim()
    if (!data || data === '[DONE]') {
      continue
    }
    let event: AgentStreamEvent
    try {
      event = JSON.parse(data) as AgentStreamEvent
    } catch {
      continue
    }
    const type = (event.type || '').toLowerCase()
    const text = event.text ?? ''
    if (type === 'text_block_delta') {
      if (text) {
        handlers.onDelta(text)
      }
      continue
    }
    if (type.includes('require_user_confirm') || type === 'require_user_confirm') {
      handlers.onStatus?.(text ? `等待确认：${text}` : '等待用户确认')
      continue
    }
    if (type.includes('tool_call') && text) {
      handlers.onStatus?.(`工具：${text}`)
    }
  }
}
