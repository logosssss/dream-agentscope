import { apiJson } from '@/shared/api/http'
import type { TaskScheduleSummary } from '@/shared/types/api.types'

/**
 * 任务调度目录。登记、停用、恢复与列表；登记时服务端算好下次触发，
 * 真正触发在用户进程（见 docs/engineering-p9-schedule-落地.md）。
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
  tenantId: string
  userId: string
  agentId: string
  cronExpr: string
  delaySeconds: number | null
}): Promise<TaskScheduleSummary> {
  const cron = body.cronExpr.trim()
  return apiJson<TaskScheduleSummary>('/api/admin/task-schedules', {
    method: 'POST',
    body: JSON.stringify({
      scheduleId: body.scheduleId,
      taskType: body.taskType,
      tenantId: body.tenantId.trim() || null,
      userId: body.userId.trim(),
      agentId: body.agentId.trim() || null,
      cronExpr: cron || null,
      delaySeconds: body.delaySeconds,
    }),
  })
}

/** 停用仍启用的调度。 */
export function disableTaskSchedule(scheduleId: string): Promise<TaskScheduleSummary> {
  return apiJson<TaskScheduleSummary>(
    `/api/admin/task-schedules/${encodeURIComponent(scheduleId)}/disable`,
    { method: 'POST' },
  )
}

/** 恢复已停用的调度。 */
export function restoreTaskSchedule(scheduleId: string): Promise<TaskScheduleSummary> {
  return apiJson<TaskScheduleSummary>(
    `/api/admin/task-schedules/${encodeURIComponent(scheduleId)}/restore`,
    { method: 'POST' },
  )
}
