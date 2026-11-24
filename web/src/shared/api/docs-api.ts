import { apiJson } from '@/shared/api/http'
import type { DocsAskResult, DocsPage, DocsSearchResult, DocsTree } from '@/shared/types/api.types'

/**
 * 文档站接口。公开阅读不需要登录；就这篇提问必须登录。
 * 调用方令牌仍按全站规则带。
 */

/** 七个主题。匿名只能看到公开文档。 */
export function fetchDocsTree(): Promise<DocsTree> {
  return apiJson<DocsTree>('/api/docs/tree')
}

/** 一篇正文。看不见时服务端是 NOT_FOUND，不是 403。 */
export function fetchDocsPage(id: string): Promise<DocsPage> {
  return apiJson<DocsPage>(`/api/docs/doc/${encodeURIComponent(id)}`)
}

/**
 * 就这一篇提问。未登录时服务端返回 UNAUTHORIZED。
 * 正文由服务端注入，这里只提交问题。
 */
export function askDocs(id: string, q: string): Promise<DocsAskResult> {
  return apiJson<DocsAskResult>(`/api/docs/doc/${encodeURIComponent(id)}/ask`, {
    method: 'POST',
    body: JSON.stringify({ q }),
  })
}

/**
 * 语义搜索。topic 空表示不限主题。
 * 回车才调用，避免每敲一个字都打一次嵌入。
 */
export function searchDocs(query: {
  q: string
  topic?: string
  source?: string
  limit?: number
}): Promise<DocsSearchResult> {
  return apiJson<DocsSearchResult>('/api/docs/search', {
    method: 'POST',
    body: JSON.stringify(query),
  })
}
