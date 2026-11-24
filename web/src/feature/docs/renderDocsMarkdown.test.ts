import { describe, expect, it } from 'vitest'
import { renderDocsMarkdown } from './renderDocsMarkdown'

/**
 * 锁住文档渲染和聊天气泡的差别：标题有 id，同仓链接换成详情地址，代码语言 class 还在。
 */
describe('renderDocsMarkdown', () => {
  it('按目录顺序写二级标题 id，并替换已算好的相对链接', () => {
    const html = renderDocsMarkdown(
      ['# 标题', '', '见 [落地](./xxx-落地.md)。', '', '## 第一节', '', '## 第一节', '', '```java', 'int a = 1;', '```'].join('\n'),
      [{ target: './xxx-落地.md', href: '/docs/doc/doc-landing' }],
      [
        { heading: '第一节', anchor: '第一节' },
        { heading: '第一节', anchor: '第一节-1' },
      ],
    )

    expect(html).toContain('href="/docs/doc/doc-landing"')
    expect(html).toContain('id="第一节"')
    expect(html).toContain('id="第一节-1"')
    expect(html).toContain('class="language-java"')
    expect(html).not.toContain('href="./xxx-落地.md"')
  })

  it('外链不在替换表里时保持原地址', () => {
    const html = renderDocsMarkdown('[站外](https://example.com/a.md)', [], [])
    expect(html).toContain('href="https://example.com/a.md"')
  })
})
