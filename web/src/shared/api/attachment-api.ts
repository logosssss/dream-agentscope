import { authHeaders, apiJson } from '@/shared/api/http'
import { clearSession } from '@/shared/auth/session'
import type { AttachmentDetail, AttachmentRefItem, AttachmentSummary } from '@/shared/types/api.types'

/**
 * 用户附件目录。列表只含仍启用；停用改 DELETED，不删对象。
 */

/** 本人本租户仍启用的附件。 */
export function listAttachments(limit = 50): Promise<AttachmentSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<AttachmentSummary[]>(`/api/attachments?${q}`)
}

/** 一条仍启用附件的 MIME、大小和登记时间。不下载正文。 */
export function getAttachment(attachmentId: string): Promise<AttachmentDetail> {
  return apiJson<AttachmentDetail>(`/api/attachments/${encodeURIComponent(attachmentId)}`)
}

/** 该附件上的引用，新的在前。不核消息、运行或文档是否存在。 */
export function listAttachmentRefs(attachmentId: string, limit = 50): Promise<AttachmentRefItem[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<AttachmentRefItem[]>(
    `/api/attachments/${encodeURIComponent(attachmentId)}/refs?${q}`,
  )
}

/** multipart 上传；不超过服务端 5MB。不设 JSON Content-Type。 */
export async function uploadAttachmentFile(file: File, sessionId?: string): Promise<AttachmentSummary> {
  const headers = authHeaders()
  headers.delete('Content-Type')
  const form = new FormData()
  form.append('file', file)
  if (sessionId && sessionId.trim()) {
    form.append('sessionId', sessionId.trim())
  }
  const response = await fetch('/api/attachments/file', {
    method: 'POST',
    headers,
    body: form,
  })
  return parseJsonResult<AttachmentSummary>(response)
}

/** 停用仍启用的附件。 */
export function disableAttachment(attachmentId: string): Promise<AttachmentSummary> {
  return apiJson<AttachmentSummary>(
    `/api/attachments/${encodeURIComponent(attachmentId)}/disable`,
    { method: 'POST' },
  )
}

/** 恢复已停用的附件。 */
export function restoreAttachment(attachmentId: string): Promise<AttachmentSummary> {
  return apiJson<AttachmentSummary>(
    `/api/attachments/${encodeURIComponent(attachmentId)}/restore`,
    { method: 'POST' },
  )
}

/**
 * 下载正文。失败仍可能是 JSON 错误体。
 * 优先用列表里的登记名；其次解析 filename*（RFC 5987），忽略 RFC 2047 的 filename=。
 */
export async function downloadAttachment(
  attachmentId: string,
  fallbackName: string,
): Promise<{ blob: Blob; fileName: string }> {
  const headers = authHeaders()
  headers.delete('Content-Type')
  const response = await fetch(
    `/api/attachments/${encodeURIComponent(attachmentId)}/content`,
    { headers },
  )
  if (response.status === 401) {
    clearSession()
    throw new Error('未授权或登录已失效，请重新登录')
  }
  const type = response.headers.get('Content-Type') || ''
  if (!response.ok || type.includes('application/json')) {
    await parseJsonResult<unknown>(response)
    throw new Error('下载失败')
  }
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition') || ''
  return { blob, fileName: resolveDownloadName(disposition, fallbackName) }
}

/**
 * 登记名优先，避免把 =?UTF-8?Q?...?= 当成磁盘文件名。
 * 没有登记名时再解 filename*。
 */
function resolveDownloadName(disposition: string, fallbackName: string): string {
  const registered = (fallbackName || '').trim()
  if (registered) {
    return registered
  }
  const star = /filename\*=(?:UTF-8''|utf-8'')([^;]+)/i.exec(disposition)
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''))
    } catch {
      // 继续回退
    }
  }
  const plain = /(?:^|;)\s*filename="([^"]+)"|(?:^|;)\s*filename=([^;]+)/i.exec(disposition)
  const raw = (plain?.[1] || plain?.[2] || '').trim()
  if (raw && !raw.startsWith('=?')) {
    return raw
  }
  return 'download'
}

/** 与 apiJson 相同的业务外壳解析，给 multipart / 非默认路径复用。 */
async function parseJsonResult<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    clearSession()
    throw new Error('未授权或登录已失效，请重新登录')
  }
  let body: { ok: boolean; code: string; message: string | null; data: T } | null = null
  const raw = await response.text()
  if (raw) {
    try {
      body = JSON.parse(raw) as { ok: boolean; code: string; message: string | null; data: T }
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
