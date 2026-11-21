/**
 * 删除请求的状态与类型的展示口径。纯函数，不碰网络，便于单测。
 *
 * 状态链条由后端定义：PENDING → APPROVED / REJECTED，APPROVED → RUNNING → DONE / FAILED。
 * 这里只负责把机器的词翻成用户能读的字，不推断后端没给过的东西。
 */

/** 状态的中文标签。未知状态原样返回，不要假装知道。 */
export function statusLabel(status: string): string {
  switch ((status || '').trim().toUpperCase()) {
    case 'PENDING':
      return '待处理'
    case 'APPROVED':
      return '已批准，等待执行'
    case 'REJECTED':
      return '已拒绝'
    case 'RUNNING':
      return '删除中'
    case 'DONE':
      return '已删除'
    case 'FAILED':
      return '执行失败'
    default:
      return status || ''
  }
}

/** 状态色：等待偏中性、执行中偏提醒、成功绿、失败红。 */
export function statusTone(status: string): 'wait' | 'progress' | 'done' | 'failed' {
  switch ((status || '').trim().toUpperCase()) {
    case 'RUNNING':
    case 'APPROVED':
      return 'progress'
    case 'DONE':
      return 'done'
    case 'FAILED':
    case 'REJECTED':
      return 'failed'
    default:
      return 'wait'
  }
}

/** 终态：不会再变了。 */
export function isTerminal(status: string): boolean {
  const value = (status || '').trim().toUpperCase()
  return value === 'DONE' || value === 'FAILED' || value === 'REJECTED'
}

/** 数据类型的中文说明。 */
export function dataTypeLabel(dataType: string): string {
  switch ((dataType || '').trim().toUpperCase()) {
    case 'SESSION':
      return '会话与消息'
    case 'ATTACHMENT':
      return '附件文件'
    case 'ALL':
      return '全部个人数据'
    default:
      return dataType || ''
  }
}

/**
 * 进度一句话。已删除时带执行时间；失败时带后端给的结果摘要。
 * 没有执行信息时不编时间。
 */
export function describeProgress(row: {
  status: string
  executedAt: string
  executedNote: string
}): string {
  const label = statusLabel(row.status)
  const at = (row.executedAt ?? '').trim()
  if (label === '已删除') {
    return at ? `已删除 · ${at}` : '已删除'
  }
  if (label === '执行失败') {
    const note = (row.executedNote ?? '').trim()
    return note ? `执行失败 · ${note}` : '执行失败'
  }
  return label
}
