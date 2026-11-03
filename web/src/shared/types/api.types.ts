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

/** GET /api/approvals/pending 一行。不带工具参数。 */
export interface PendingApproval {
  approvalId: string
  toolId: string
  sessionId: string
}

/** POST /api/approvals/{id}/decision 回传摘要。 */
export interface ApprovalDecisionView {
  approvalId: string
  toolId: string
  sessionId: string
  decision: string
}
