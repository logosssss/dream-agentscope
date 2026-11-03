import { hasAdminToken, loadOperatorId } from '@/shared/auth/operator'
import type { ApiResult } from '@/shared/types/api.types'

/**
 * 管理端 JSON 调用。带 X-Caller-Token（Admin Token）与可选 X-Operator-Id。
 */

/** 组装鉴权头。缺 Admin Token 直接抛错。 */
export function adminHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra)
  if (!hasAdminToken()) {
    throw new Error('未配置 VITE_ADMIN_TOKEN，请复制 web-admin/.env.example 为 .env.local 并填写')
  }
  headers.set('X-Caller-Token', import.meta.env.VITE_ADMIN_TOKEN.trim())
  const operator = loadOperatorId()
  if (operator) {
    headers.set('X-Operator-Id', operator)
  }
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return headers
}

/**
 * JSON API。401/503/ok=false 都转成 Error。
 */
export async function apiJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: adminHeaders(init?.headers),
  })

  let body: ApiResult<T> | null = null
  const raw = await response.text()
  if (raw) {
    try {
      body = JSON.parse(raw) as ApiResult<T>
    } catch {
      throw new Error(`响应不是 JSON（HTTP ${response.status}）`)
    }
  }

  if (response.status === 401) {
    throw new Error(body?.message || '未授权：请检查 VITE_ADMIN_TOKEN 是否与 AGENTSCOPE_ADMIN_TOKEN 一致')
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
    throw new Error(body.message || body.code || '业务失败')
  }

  return body.data
}
