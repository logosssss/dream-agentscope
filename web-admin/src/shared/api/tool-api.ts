import { apiJson } from '@/shared/api/http'
import type { ToolPermissionSummary, ToolRegistrySummary } from '@/shared/types/api.types'

/**
 * 工具目录与权限规则。只登记与列表，不执行工具。
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

/** 最近权限规则，含停用。 */
export function listToolPermissions(limit = 50): Promise<ToolPermissionSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<ToolPermissionSummary[]>(`/api/admin/tool-permissions?${q}`)
}

/**
 * 登记一条。空白租户/角色按全局 `*`；空白优先级按 100。
 * conditions 不传，由服务端固定为空。
 */
export function registerToolPermission(body: {
  toolId: string
  tenantId: string
  roleId: string
  decision: string
  priority: string
}): Promise<ToolPermissionSummary> {
  const raw = body.priority.trim()
  return apiJson<ToolPermissionSummary>('/api/admin/tool-permissions', {
    method: 'POST',
    body: JSON.stringify({
      toolId: body.toolId,
      tenantId: body.tenantId || null,
      roleId: body.roleId || null,
      decision: body.decision,
      priority: raw ? Number.parseInt(raw, 10) : null,
    }),
  })
}

/** 共用租户/角色体；空白按全局。 */
function scopeBody(tenantId: string, roleId: string) {
  return {
    tenantId: tenantId || null,
    roleId: roleId || null,
  }
}

/** 停用仍启用的规则。 */
export function disableToolPermission(
  toolId: string,
  tenantId: string,
  roleId: string,
): Promise<ToolPermissionSummary> {
  return apiJson<ToolPermissionSummary>(
    `/api/admin/tool-permissions/${encodeURIComponent(toolId)}/disable`,
    {
      method: 'POST',
      body: JSON.stringify(scopeBody(tenantId, roleId)),
    },
  )
}

/** 恢复已停用的规则。 */
export function restoreToolPermission(
  toolId: string,
  tenantId: string,
  roleId: string,
): Promise<ToolPermissionSummary> {
  return apiJson<ToolPermissionSummary>(
    `/api/admin/tool-permissions/${encodeURIComponent(toolId)}/restore`,
    {
      method: 'POST',
      body: JSON.stringify(scopeBody(tenantId, roleId)),
    },
  )
}

/** 改优先级；不改决定与启用。 */
export function updateToolPermissionPriority(
  toolId: string,
  tenantId: string,
  roleId: string,
  priority: number,
): Promise<ToolPermissionSummary> {
  return apiJson<ToolPermissionSummary>(
    `/api/admin/tool-permissions/${encodeURIComponent(toolId)}/priority`,
    {
      method: 'POST',
      body: JSON.stringify({
        ...scopeBody(tenantId, roleId),
        priority,
      }),
    },
  )
}
