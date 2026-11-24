import type { DocsResolvedLink, DocsTocEntry } from '@/shared/types/api.types'
import MarkdownIt from 'markdown-it'

/**
 * 文档页 Markdown。和聊天气泡分开：要写标题 id，并套用服务端算好的文档链接。
 * 不开启 html，代码块只保留语言 class，不在这里做高亮。
 */
const markdown = new MarkdownIt({
  html: false,
  linkify: false,
  breaks: false,
})

interface RenderEnv {
  h2: number
  anchors: string[]
  links: Map<string, string>
  [key: string]: unknown
}

/** markdown-it 会把链接里的汉字编成百分号。服务端给的是原文，两边都试一次。 */
function mappedHref(href: string, links: Map<string, string>): string | undefined {
  const direct = links.get(href)
  if (direct) {
    return direct
  }
  try {
    return links.get(decodeURI(href))
  } catch {
    return undefined
  }
}

markdown.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  if (token && token.tag === 'h2' && env) {
    const state = env as unknown as RenderEnv
    const anchor = state.anchors[state.h2]
    state.h2 += 1
    if (anchor) {
      token.attrSet('id', anchor)
    }
  }
  return self.renderToken(tokens, idx, options)
}

markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const href = token?.attrGet('href')
  if (token && env && typeof href === 'string') {
    const next = mappedHref(href, (env as unknown as RenderEnv).links)
    if (next) {
      token.attrSet('href', next)
    }
  }
  return self.renderToken(tokens, idx, options)
}

/**
 * 渲染一篇正文。二级标题按目录顺序写 id，相对链接只替换服务端给出的 target。
 * 没对上的链接保持原文，避免把外链或图片改到文档路由。
 */
export function renderDocsMarkdown(
  source: string,
  links: DocsResolvedLink[] | null | undefined,
  toc: DocsTocEntry[] | null | undefined,
): string {
  const map = new Map<string, string>()
  for (const link of links ?? []) {
    if (link?.target && link.href && !map.has(link.target)) {
      map.set(link.target, link.href)
    }
  }
  const env: RenderEnv = {
    h2: 0,
    anchors: (toc ?? []).map((entry) => entry.anchor).filter((anchor) => Boolean(anchor)),
    links: map,
  }
  return markdown.render(source ?? '', env as unknown as Record<PropertyKey, unknown>)
}
