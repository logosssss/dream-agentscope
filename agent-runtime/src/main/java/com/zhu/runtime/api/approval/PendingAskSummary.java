package com.zhu.runtime.api.approval;

/**
 * 运行层对外待确认摘要。不含 AgentScope 类型与工具参数；供接入层 HTTP 映射。
 *
 * @param approvalId 审批单标识（工具调用 id）
 * @param toolName   工具名
 * @param sessionId  发起会话
 */
public record PendingAskSummary(String approvalId, String toolName, String sessionId) {
}
