/**
 * 把问句在片段里标出来。大小写不敏感，问句为空时不高亮。
 * 不用 HTML 字符串拼接，避免把片段里的尖括号当成标签。
 */
export function highlightParts(snippet: string, query: string): { text: string; hit: boolean }[] {
  const text = snippet ?? ''
  const needle = query.trim()
  if (!text || !needle) {
    return [{ text, hit: false }]
  }
  const lower = text.toLowerCase()
  const target = needle.toLowerCase()
  const parts: { text: string; hit: boolean }[] = []
  let cursor = 0
  let at = lower.indexOf(target, cursor)
  while (at >= 0) {
    if (at > cursor) {
      parts.push({ text: text.slice(cursor, at), hit: false })
    }
    parts.push({ text: text.slice(at, at + needle.length), hit: true })
    cursor = at + needle.length
    at = lower.indexOf(target, cursor)
  }
  if (cursor < text.length) {
    parts.push({ text: text.slice(cursor), hit: false })
  }
  return parts.length === 0 ? [{ text, hit: false }] : parts
}
