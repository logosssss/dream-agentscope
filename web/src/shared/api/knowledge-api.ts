import { ApiBusinessError, apiJson, authHeaders } from '@/shared/api/http'
import { clearSession } from '@/shared/auth/session'
import type { KnowledgeBaseDetail, KnowledgeBaseItem, KnowledgeDocumentItem } from '@/shared/types/api.types'

/**
 * 用户私有知识库。建库、查看，写入纯文本，或上传文本文件。
 * 不删库。
 */

/** 本人本租户的库。MySQL 关闭时是空列表。 */
export function listKnowledgeBases(): Promise<KnowledgeBaseItem[]> {
  return apiJson<KnowledgeBaseItem[]>('/api/kb')
}

/** 一条库的创建时间。不读文档。 */
export function getKnowledgeBase(kbId: string): Promise<KnowledgeBaseDetail> {
  return apiJson<KnowledgeBaseDetail>(`/api/kb/${encodeURIComponent(kbId)}`)
}

/**
 * 建库。名称为空或超长由服务端拒绝。
 * 不传 agentId，绑定默认搭子。
 */
export function createKnowledgeBase(name: string): Promise<KnowledgeBaseItem> {
  return apiJson<KnowledgeBaseItem>('/api/kb', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

/** 某库每篇文档的最新一版。不含正文。 */
export function listKnowledgeDocuments(kbId: string): Promise<KnowledgeDocumentItem[]> {
  return apiJson<KnowledgeDocumentItem[]>(`/api/kb/${encodeURIComponent(kbId)}/documents`)
}

/**
 * 新建一篇纯文本。不传 docId。正文 UTF-8 不超过 2MB。
 * 写入后页面会自己刷新索引状态。
 */
export function createKnowledgeDocument(
  kbId: string,
  title: string,
  content: string,
): Promise<KnowledgeDocumentItem> {
  return apiJson<KnowledgeDocumentItem>('/api/kb/documents', {
    method: 'POST',
    body: JSON.stringify({ kbId, title, content }),
  })
}

/**
 * 上传一篇纯文本文件。不传 docId。
 * 扩展名只接受 txt、md、markdown、csv、json、log、yml、yaml、xml。
 * 标题可空，空则由服务端用文件名。
 */
export async function uploadKnowledgeFile(
  kbId: string,
  file: File,
  title?: string,
): Promise<KnowledgeDocumentItem> {
  const headers = authHeaders()
  headers.delete('Content-Type')
  const form = new FormData()
  form.append('kbId', kbId)
  form.append('file', file)
  const trimmed = (title || '').trim()
  if (trimmed) {
    form.append('title', trimmed)
  }
  const response = await fetch('/api/kb/documents/file', {
    method: 'POST',
    headers,
    body: form,
  })
  return parseJsonResult<KnowledgeDocumentItem>(response)
}

/** multipart 的业务外壳。503 仍是普通 Error，业务失败带 code。 */
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
    throw new ApiBusinessError(body.code || 'ERROR', body.message || body.code || '业务失败')
  }
  return body.data
}

/**
 * 重试仍待索引或已失败的某一版。不改正文。
 * 索引中、已成功或已归档由服务端拒绝。
 */
export function reindexKnowledgeDocument(docId: string, version: number): Promise<KnowledgeDocumentItem> {
  const q = new URLSearchParams({ version: String(version) })
  return apiJson<KnowledgeDocumentItem>(
    `/api/kb/documents/${encodeURIComponent(docId)}/reindex?${q}`,
    { method: 'POST' },
  )
}

/** 删除一篇文档的全部版本、切块和对象。不删库。找不到按业务错误。 */
export function deleteKnowledgeDocument(docId: string): Promise<void> {
  return apiJson<void>(`/api/kb/documents/${encodeURIComponent(docId)}`, { method: 'DELETE' })
}

/** 删除整个库及其文档、切块和对象。别人的库按找不到。 */
export function deleteKnowledgeBase(kbId: string): Promise<void> {
  return apiJson<void>(`/api/kb/${encodeURIComponent(kbId)}`, { method: 'DELETE' })
}
