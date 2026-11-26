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

/** GET /api/admin/tenant-users 一行。时间为空串表示这次响应没带回库里的列。 */
export interface TenantUserSummary {
  tenantId: string
  userId: string
  displayName: string | null
  status: string
  createdAt: string
  updatedAt: string
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

/** GET /api/admin/quota-usage 一行。不是剩余额度。 */
export interface QuotaUsageRow {
  id: number
  tenantId: string
  userId: string | null
  quotaType: string
  amount: number
  reason: string | null
  createdAt: string
}

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
  inputPer1m: number | string
  outputPer1m: number | string
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

/** GET /api/admin/operation-audits 一行。不含前后快照。createdAt 为空串表示登记响应还没回读。 */
export interface OperationAuditSummary {
  operatorId: string
  tenantId: string
  operation: string
  objectType: string
  objectId: string
  createdAt: string
}

/**
 * GET /api/admin/audits 一行：执行内核落的事件审计。
 * runId 为空说明这行不是某一轮运行写的（任务类动作就没有）。
 */
export interface AuditEventRow {
  eventId: string
  eventType: string
  userId: string
  sessionId: string
  runId: string
  source: string
  payload: string
  createdAt: string
}

/**
 * GET /api/admin/runs 一行：管理端看到的一次运行。
 * requestId 为空说明调用方没带请求号。
 */
export interface RunRow {
  runId: string
  tenantId: string
  userId: string
  sessionId: string
  requestId: string
  /** 来源地址。老运行和没抄到地址时是空串。 */
  clientIp: string
  triggerType: string
  status: string
  model: string
  inputSummary: string
  outputSummary: string
  errorCode: string
  durationMs: number | null
  startedAt: string
  finishedAt: string
}

/** GET /api/admin/runs/{runId} 里的一个步骤。 */
export interface RunStepRow {
  stepNo: number | null
  stepType: string
  toolId: string
  status: string
}

/** 一轮运行的详情：摘要、步骤，以及按运行号加总的用量。 */
export interface RunUsage {
  inputTokens: number
  outputTokens: number
  totalTokens: number
  costAmount: number | string
  currency: string
  /** false 表示这一轮没有用量行，不是花了 0 token。 */
  recorded: boolean
}

/** 一轮运行的详情：摘要 + 步骤 + 用量。 */
export interface RunDetail extends RunRow {
  steps: RunStepRow[]
  usage: RunUsage
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
  tenantId: string
  userId: string
  agentId: string
  cronExpr: string | null
  delaySeconds: number | null
  enabled: boolean
  /** 下次触发时间，ISO 本地时间；空串表示还没算出来。 */
  nextFireAt: string
  /** 上次触发时间；空串表示从未触发。 */
  lastFireAt: string
  /** 上次触发结果：SUCCEEDED / FAILED；空串表示从未触发。 */
  lastStatus: string
}

/** GET /api/admin/eval-datasets 一行。不含用例。 */
export interface EvalDatasetSummary {
  datasetId: string
  name: string
  version: string
  description: string | null
  enabled: boolean
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
  /** 执行时间；还没执行时为空串。 */
  executedAt: string
  /** 执行结果摘要；还没执行时为空串。 */
  executedNote: string
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
