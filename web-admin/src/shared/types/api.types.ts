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

/** GET /api/admin/approvals 一行。不含工具参数。 */
export interface ApprovalRow {
  approvalId: string
  userId: string
  sessionId: string
  toolName: string
  decision: string
  requestedAt: string | null
  approver: string | null
  channel: string | null
  reason: string | null
  decidedAt: string | null
}

/**
 * 租户额度一行。类型/周期随接口固定；
 * COST 的 limit 为金额微单位（×1e6）；0 表示不限额。
 */
export interface TenantQuotaSummary {
  tenantId: string
  quotaType: string
  period: string
  limit: number
}

/** 额度种类，对应四套管理接口。 */
export type QuotaKind = 'requests' | 'tokens' | 'cost' | 'concurrent'

/** GET /api/admin/model-configs 一行。不含参数 JSON。 */
export interface ModelConfigSummary {
  configId: string
  model: string
  provider: string
  version: string
  tenantScope: string
  enabled: boolean
}

/** GET /api/admin/model-prices 一行。 */
export interface ModelPriceEntry {
  id: number
  model: string
  currency: string
  inputPer1k: number | string
  outputPer1k: number | string
  effectiveFrom: string | null
  effectiveTo: string | null
}

/** GET /api/admin/safety-interventions 一行。不含用户原文。 */
export interface SafetyInterventionSummary {
  tenantId: string
  userId: string
  sessionId: string
  riskLevel: string
  source: string
  summary: string
}

/** GET /api/admin/operation-audits 一行。不含前后快照。 */
export interface OperationAuditSummary {
  operatorId: string
  tenantId: string
  operation: string
  objectType: string
  objectId: string
}

/** GET /api/admin/retention-policies 一行。不表示已执行清理。 */
export interface RetentionPolicySummary {
  dataType: string
  scope: string
  retainDays: number
  action: string
  enabled: boolean
}

/** GET /api/admin/task-schedules 一行。不表示已触发。 */
export interface TaskScheduleSummary {
  scheduleId: string
  taskType: string
  cronExpr: string | null
  delaySeconds: number | null
}

/** GET /api/admin/eval-datasets 一行。不含用例。 */
export interface EvalDatasetSummary {
  datasetId: string
  name: string
  version: string
  description: string | null
}

/** POST 追加用例后的一行。不含执行结果。 */
export interface EvalCaseSummary {
  datasetId: string
  version: string
  caseNo: number
  inputText: string
  expectedText: string | null
}

/** POST 登记/打分执行后的一行。不调用模型。 */
export interface EvalRunSummary {
  evalRunId: string
  datasetId: string
  version: string
  totalCases: number
  passedCases: number
  status: string
}

/** POST 账单草稿/确认/开票/付款后的一行。无列表接口。 */
export interface BillingSummary {
  tenantId: string
  periodStart: string
  periodEnd: string
  tokenTotal: number
  costAmount: number | string
  currency: string
  status: string
}

/** POST 删除请求决定后的一行。管理端无列表接口；不表示已删数据。 */
export interface DeletionRequestSummary {
  requestId: string
  userId: string
  tenantId: string
  dataType: string
  status: string
  reason: string | null
}

/** GET /api/admin/tool-registries 一行。不含 param_schema。 */
export interface ToolRegistrySummary {
  toolId: string
  name: string
  version: string
  module: string
  description: string
  enabled: boolean
}

/** GET /api/admin/tool-permissions 一行。不含 conditions。 */
export interface ToolPermissionSummary {
  toolId: string
  tenantId: string
  roleId: string
  decision: string
  priority: number
  enabled: boolean
}
