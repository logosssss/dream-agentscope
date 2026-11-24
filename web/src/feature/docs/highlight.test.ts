import { describe, expect, it } from 'vitest'
import { highlightParts } from './highlight'

describe('highlightParts', () => {
  it('标出问句，大小写不影响原文', () => {
    expect(highlightParts('为什么 Gate 重要', 'gate')).toEqual([
      { text: '为什么 ', hit: false },
      { text: 'Gate', hit: true },
      { text: ' 重要', hit: false },
    ])
  })

  it('空问句整段都不标', () => {
    expect(highlightParts('原文', '  ')).toEqual([{ text: '原文', hit: false }])
  })
})
