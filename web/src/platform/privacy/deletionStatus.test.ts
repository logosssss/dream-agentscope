import { describe, expect, it } from 'vitest'
import {
  dataTypeLabel,
  describeProgress,
  isTerminal,
  statusLabel,
  statusTone,
} from './deletionStatus'

/** 状态词是后端契约，翻错会让用户以为数据已经没了。 */
describe('deletionStatus', () => {
  it('把六种状态翻成中文，未知状态原样返回', () => {
    expect(statusLabel('PENDING')).toBe('待处理')
    expect(statusLabel('approved')).toBe('已批准，等待执行')
    expect(statusLabel('REJECTED')).toBe('已拒绝')
    expect(statusLabel('RUNNING')).toBe('删除中')
    expect(statusLabel('DONE')).toBe('已删除')
    expect(statusLabel('FAILED')).toBe('执行失败')
    expect(statusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
    expect(statusLabel('')).toBe('')
  })

  it('失败与拒绝都是终态，等待与执行中不是', () => {
    expect(isTerminal('DONE')).toBe(true)
    expect(isTerminal('FAILED')).toBe(true)
    expect(isTerminal('REJECTED')).toBe(true)
    expect(isTerminal('PENDING')).toBe(false)
    expect(isTerminal('APPROVED')).toBe(false)
    expect(isTerminal('RUNNING')).toBe(false)
  })

  it('配色不把失败画成成功', () => {
    expect(statusTone('DONE')).toBe('done')
    expect(statusTone('FAILED')).toBe('failed')
    expect(statusTone('RUNNING')).toBe('progress')
    expect(statusTone('PENDING')).toBe('wait')
  })

  it('数据类型有中文说明', () => {
    expect(dataTypeLabel('SESSION')).toBe('会话与消息')
    expect(dataTypeLabel('ATTACHMENT')).toBe('附件文件')
    expect(dataTypeLabel('ALL')).toBe('全部个人数据')
    expect(dataTypeLabel('OTHER')).toBe('OTHER')
  })

  it('已删除带执行时间，失败带摘要，没执行就不编时间', () => {
    expect(
      describeProgress({ status: 'DONE', executedAt: '2026-11-21T10:00:00', executedNote: 'deleted=3' }),
    ).toBe('已删除 · 2026-11-21T10:00:00')
    expect(
      describeProgress({ status: 'DONE', executedAt: '', executedNote: '' }),
    ).toBe('已删除')
    expect(
      describeProgress({ status: 'FAILED', executedAt: '2026-11-21T10:00:00', executedNote: 'boom' }),
    ).toBe('执行失败 · boom')
    expect(
      describeProgress({ status: 'APPROVED', executedAt: '', executedNote: '' }),
    ).toBe('已批准，等待执行')
  })
})
