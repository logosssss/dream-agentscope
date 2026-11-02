import { clearSession, loadSession } from '@/shared/auth/session'
import type { ApiResult } from '@/shared/types/api.types'

/**
 * 带调用方令牌与可选 Bearer 的 fetch 封装。
 * 聊天流式请走 useStreaming，不要用本模块的 json 解析。
 */

/** 组装鉴权头。缺 Caller Token 直接抛错，避免静默 401。 */
export function authHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra)
  const caller = import.meta.env.VITE_CALLER_TOKEN
  if (!caller || caller.trim() === '') {
    throw new Error('未配置 VITE_CALLER_TOKEN，请复制 web/.env.example 为 .env.local 并填写')
  }
  headers.set('X-Caller-Token', caller.trim())
  const session = loadSession()
  if (session) {
    headers.set('Authorization', `Bearer ${session.accessToken}`)
  }
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return headers
}

/**
 * JSON API 调用。HTTP 401 清会话；503 与 ok=false 都转成 Error。
 */
export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: authHeaders(init?.headers),
  })

  if (response.status === 401) {
    clearSession()
    throw new Error('未授权或登录已失效，请重新登录')
  }

  let body: ApiResult<T> | null = null
  const raw = await response.text()
  if (raw) {
    try {
      body = JSON.parse(raw) as ApiResult<T>
    } catch {
      throw new Error(`响应不是 JSON（HTTP ${response.status}）`)
    }
  }

  if (response.status === 503) {
    throw new Error(body?.message || '服务暂不可用（503）')
  }

  if (!response.ok) {
    throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  }

  if (!body) {
    throw new Error('响应体为空')
  }

  if (!body.ok) {
    if (body.code === 'UNAUTHORIZED') {
      clearSession()
    }
    throw new Error(body.message || body.code || '业务失败')
  }

  return body.data
}
