import { apiJson } from '@/shared/api/http'
import type { TenantSummary, TenantUserSummary } from '@/shared/types/api.types'

/**
 * 租户与租户用户管理接口。身份靠 Admin Token。
 */

/** 最近登记的租户。 */
export function listTenants(limit = 50): Promise<TenantSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<TenantSummary[]>(`/api/admin/tenants?${q}`)
}

/** 登记可用租户。 */
export function registerTenant(tenantId: string, name: string): Promise<TenantSummary> {
  return apiJson<TenantSummary>('/api/admin/tenants', {
    method: 'POST',
    body: JSON.stringify({ tenantId, name }),
  })
}

/** 停用租户。 */
export function suspendTenant(tenantId: string): Promise<TenantSummary> {
  return apiJson<TenantSummary>(`/api/admin/tenants/${encodeURIComponent(tenantId)}/suspend`, {
    method: 'POST',
  })
}

/** 关闭租户。 */
export function closeTenant(tenantId: string): Promise<TenantSummary> {
  return apiJson<TenantSummary>(`/api/admin/tenants/${encodeURIComponent(tenantId)}/close`, {
    method: 'POST',
  })
}

/** 恢复租户为可用。 */
export function restoreTenant(tenantId: string): Promise<TenantSummary> {
  return apiJson<TenantSummary>(`/api/admin/tenants/${encodeURIComponent(tenantId)}/restore`, {
    method: 'POST',
  })
}

/** 某租户最近用户；tenantId 必填。 */
export function listTenantUsers(tenantId: string, limit = 50): Promise<TenantUserSummary[]> {
  const q = new URLSearchParams({ tenantId, limit: String(limit) })
  return apiJson<TenantUserSummary[]>(`/api/admin/tenant-users?${q}`)
}

/** 给已有租户挂用户。 */
export function registerTenantUser(
  tenantId: string,
  userId: string,
  displayName: string,
): Promise<TenantUserSummary> {
  return apiJson<TenantUserSummary>('/api/admin/tenant-users', {
    method: 'POST',
    body: JSON.stringify({ tenantId, userId, displayName: displayName || null }),
  })
}

/** 停用用户。 */
export function disableTenantUser(tenantId: string, userId: string): Promise<TenantUserSummary> {
  return apiJson<TenantUserSummary>(
    `/api/admin/tenant-users/${encodeURIComponent(tenantId)}/${encodeURIComponent(userId)}/disable`,
    { method: 'POST' },
  )
}

/** 恢复用户。 */
export function restoreTenantUser(tenantId: string, userId: string): Promise<TenantUserSummary> {
  return apiJson<TenantUserSummary>(
    `/api/admin/tenant-users/${encodeURIComponent(tenantId)}/${encodeURIComponent(userId)}/restore`,
    { method: 'POST' },
  )
}
