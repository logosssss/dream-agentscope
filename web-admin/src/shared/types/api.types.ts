/**
 * 与管理进程 ApiResult / 租户目录对齐的类型。
 */

/** 统一响应外壳。 */
export interface ApiResult<T> {
  ok: boolean
  code: string
  message: string | null
  data: T
}

/** GET /api/admin/tenants 一行。 */
export interface TenantSummary {
  tenantId: string
  name: string
  plan: string
  status: string
}

/** GET /api/admin/tenant-users 一行。 */
export interface TenantUserSummary {
  tenantId: string
  userId: string
  displayName: string | null
  status: string
}
