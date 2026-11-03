/**
 * 与接入层 ApiResult / 登录载荷对齐的前端类型。
 */

/** 统一响应外壳。业务失败多数仍是 HTTP 200 + ok=false。 */
export interface ApiResult<T> {
  ok: boolean
  code: string
  message: string | null
  data: T
}

/** POST /api/login 成功载荷。 */
export interface LoginResponse {
  accessToken: string
  expiresIn: number
  tenantId: string
  userId: string
  displayName: string
}

/** SSE data 字段解析后的流式事件。 */
export interface AgentStreamEvent {
  type: string
  text: string
}

/** 聊天请求体。身份靠 Bearer，不传 tenantId/userId。 */
export interface ChatStreamRequest {
  agentId: string
  sessionId: string
  text: string
}

/** GET /api/sessions 一行。不含正文。 */
export interface SessionSummary {
  sessionId: string
  title: string
  channel: string
  lastActiveAt: string
}

/**
 * GET /api/sessions/{id}。不含对话正文。
 * lastRunId 为空串表示还没有运行。
 */
export interface SessionDetail {
  sessionId: string
  title: string
  channel: string
  status: string
  lastRunId: string | null
  messageCount: number
  lastActiveAt: string
}

/**
 * GET /api/runs/{runId} 里的摘要。
 * 侧栏不展示输入输出摘要。
 */
export interface RunSummary {
  runId: string
  tenantId: string
  userId: string
  sessionId: string
  triggerType: string
  status: string
  model: string | null
  inputSummary: string | null
  outputSummary: string | null
  errorCode: string | null
  durationMs: number | null
}

/** 一步运行。页面不展示，避免把工具名铺开。 */
export interface RunStepSummary {
  stepNo: number
  stepType: string
  toolId: string | null
  status: string
}

/**
 * GET /api/runs/{runId}。时间为空串表示没有。
 * 不含步骤参数和对话全文。
 */
export interface RunDetail {
  run: RunSummary
  steps: RunStepSummary[]
  startedAt: string
  finishedAt: string
}

/** GET /api/sessions/{id}/messages 一条气泡。按 seq 升序。 */
export interface ChatMessageItem {
  seq: number
  role: string
  content: string
  runId: string | null
  createdAt: string
}

/** GET /api/attachments 一行。不表示对象已落地。 */
export interface AttachmentSummary {
  attachmentId: string
  tenantId: string
  userId: string
  sessionId: string | null
  fileName: string
  storageType: string
  storagePath: string
  status: string
}

/**
 * GET /api/attachments/{id}。只含仍启用的行。
 * 比列表多 MIME、大小和登记时间。页面不展示存储路径。
 */
export interface AttachmentDetail {
  attachmentId: string
  tenantId: string
  userId: string
  sessionId: string | null
  fileName: string
  storageType: string
  storagePath: string
  mimeType: string | null
  sizeBytes: number | null
  status: string
  createdAt: string
}

/** GET /api/attachments/{id}/refs 一行。不表示消息、运行或文档一定还在。 */
export interface AttachmentRefItem {
  refType: string
  refId: string
  createdAt: string
}

/** GET /api/approvals/pending 一行。不带工具参数。 */
export interface PendingApproval {
  approvalId: string
  toolId: string
  sessionId: string
}

/**
 * GET /api/approvals/{approvalId}。含已决定行。
 * 不带工具参数和执行结果。时间为空串表示没有。
 */
export interface ApprovalDetail {
  approvalId: string
  toolId: string
  sessionId: string
  decision: string
  requestedAt: string
  decidedAt: string
  reason: string | null
}

/** POST /api/approvals/{id}/decision 回传摘要。 */
export interface ApprovalDecisionView {
  approvalId: string
  toolId: string
  sessionId: string
  decision: string
}

/** GET /api/notifications 一行。不表示已经送到。 */
export interface ApprovalNotice {
  id: number
  approvalId: string
  channel: string
  sendStatus: string
}

/**
 * GET /api/notifications/{id}。含已记录或失败的行。
 * 时间为空串表示没有。不改发送状态。
 */
export interface ApprovalNoticeDetail {
  id: number
  approvalId: string
  channel: string
  sendStatus: string
  createdAt: string
}

/** GET /api/todos 一行。只含还开着的，不带截止时间。 */
export interface UserTodoRow {
  id: number
  todoType: string
  refId: string | null
  title: string | null
  status: string
}

/** GET /api/todos/{id}。含已关闭行和创建时间。不带截止时间。 */
export interface UserTodoDetail {
  id: number
  todoType: string
  refId: string | null
  title: string | null
  status: string
  createdAt: string
}

/** GET /api/usage 一行。不带提示词或回复。 */
export interface TokenUsageRow {
  id: number
  runId: string | null
  sessionId: string | null
  model: string | null
  inputTokens: number | null
  outputTokens: number | null
  totalTokens: number | null
  costAmount: number | string | null
  currency: string | null
}

/** GET /api/usage/{id}。比列表多记账时间。不改金额，也不带提示词。 */
export interface TokenUsageDetail {
  id: number
  runId: string | null
  sessionId: string | null
  model: string | null
  inputTokens: number | null
  outputTokens: number | null
  totalTokens: number | null
  costAmount: number | string | null
  currency: string | null
  createdAt: string
}

/** GET /api/feedback 一行。不带对话正文，也不表示运行仍在。 */
export interface FeedbackSummary {
  id: number
  runId: string | null
  rating: number
  comment: string | null
}

/** GET /api/feedback/{id}。比列表多创建时间。 */
export interface FeedbackDetail {
  id: number
  runId: string | null
  rating: number
  comment: string | null
  createdAt: string
}

/** GET /api/quota 的一项。limit 为空表示没有这项上限；0 表示不限额。 */
export interface QuotaMeter {
  used: number
  limit: number | null
}

/** GET /api/quota。只读当日计数，不扣额度。 */
export interface QuotaSnapshot {
  day: string
  requests: QuotaMeter
  tokens: QuotaMeter
  cost: QuotaMeter
  concurrent: QuotaMeter
}

/** GET /api/kb 一行。不含文档。 */
export interface KnowledgeBaseItem {
  kbId: string
  tenantId: string
  userId: string
  agentId: string
  name: string
}

/** GET /api/kb/{kbId}。比列表多创建时间，不读文档。 */
export interface KnowledgeBaseDetail {
  kbId: string
  tenantId: string
  userId: string
  agentId: string
  name: string
  createdAt: string
}

/**
 * GET /api/kb/{kbId}/documents 一行，以及写入后的回执。
 * 列表不含正文。页面不展示 content。
 */
export interface KnowledgeDocumentItem {
  docId: string
  version: number
  kbId: string
  agentId: string
  title: string | null
  content: string | null
  status: string
  errorMsg: string | null
}
