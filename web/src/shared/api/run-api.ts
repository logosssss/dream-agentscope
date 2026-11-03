import { apiJson } from '@/shared/api/http'
import type { RunDetail } from '@/shared/types/api.types'

/**
 * 当前用户已经结束的运行。不拉步骤参数，也不发起新运行。
 */

/** 本人本租户的一次运行。别人的按找不到。 */
export function getRun(runId: string): Promise<RunDetail> {
  return apiJson<RunDetail>(`/api/runs/${encodeURIComponent(runId)}`)
}
