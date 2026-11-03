import { apiJson } from '@/shared/api/http'
import type { TaskScheduleSummary } from '@/shared/types/api.types'

/**
 * 任务调度目录。只登记与列表，不计算下次触发，也不消费。
 */

/** 最近任务调度，含停用。 */
export function listTaskSchedules(limit = 50): Promise<TaskScheduleSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<TaskScheduleSummary[]>(`/api/admin/task-schedules?${q}`)
}

/**
 * 登记一条。cron 与 delaySeconds 必须只给其中一个。
 * 同一 scheduleId 冲突由服务端返回。
 */
export function registerTaskSchedule(body: {
  scheduleId: string
  taskType: string
  cronExpr: string
  delaySeconds: number | null
}): Promise<TaskScheduleSummary> {
  const cron = body.cronExpr.trim()
  return apiJson<TaskScheduleSummary>('/api/admin/task-schedules', {
    method: 'POST',
    body: JSON.stringify({
      scheduleId: body.scheduleId,
      taskType: body.taskType,
      cronExpr: cron || null,
      delaySeconds: body.delaySeconds,
    }),
  })
}
