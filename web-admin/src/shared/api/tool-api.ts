import { apiJson } from '@/shared/api/http'
import type { ToolRegistrySummary } from '@/shared/types/api.types'

/**
 * 工具目录。只登记与列表，不执行工具，也不写权限。
 */

/** 最近工具目录，含停用。 */
export function listToolRegistries(limit = 50): Promise<ToolRegistrySummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<ToolRegistrySummary[]>(`/api/admin/tool-registries?${q}`)
}

/** 登记 1.0.0；同一 toolId 冲突由服务端返回。 */
export function registerToolRegistry(body: {
  toolId: string
  name: string
  module: string
  description: string
}): Promise<ToolRegistrySummary> {
  return apiJson<ToolRegistrySummary>('/api/admin/tool-registries', {
    method: 'POST',
    body: JSON.stringify({
      toolId: body.toolId,
      name: body.name,
      module: body.module || null,
      description: body.description || null,
    }),
  })
}
