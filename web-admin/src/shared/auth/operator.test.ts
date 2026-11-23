import { beforeEach, describe, expect, it } from 'vitest'
import { loadOperatorId, saveOperatorId } from './operator'

/**
 * 操作人记忆：写空等于删键，读写都去首尾空白。
 * 这是管理端审计「谁点的」的唯一来源，空白值不能落库成看起来像账号的空串。
 */
describe('operatorId', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('保存后能读回去掉空白的值', () => {
    saveOperatorId('  ops-7  ')
    expect(loadOperatorId()).toBe('ops-7')
  })

  it('保存空白会删键', () => {
    saveOperatorId('ops-7')
    saveOperatorId('   ')
    expect(loadOperatorId()).toBe('')
  })

  it('没有值时返回空串', () => {
    expect(loadOperatorId()).toBe('')
  })
})
