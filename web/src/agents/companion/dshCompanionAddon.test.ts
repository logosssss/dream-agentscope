import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 搭子页的 DeepSeek Harness 入口不进 Vue 包。
 * 整目录替换 web-dist 时，index.html 的 script 和 public 里的文件必须都在，否则按钮会被换掉。
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')

describe('dsh companion addon', () => {
  it('index.html 引用 public 里的入口脚本', () => {
    const html = readFileSync(resolve(webRoot, 'index.html'), 'utf8')
    const addon = readFileSync(resolve(webRoot, 'public/dsh-companion-addon.js'), 'utf8')

    expect(html).toContain('<script src="/dsh-companion-addon.js" defer></script>')
    expect(addon).toContain('DeepSeek Harness')
    expect(addon).toContain('/api/dsh-gate/ticket')
    expect(addon).toContain('agentscope.loginToken')
    expect(addon).toContain('agentscope.userId')
  })
})
