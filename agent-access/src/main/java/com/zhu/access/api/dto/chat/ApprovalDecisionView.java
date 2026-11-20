package com.zhu.access.api.dto.chat;

/**
 * 用户端决定接口回传的摘要。不带审批人、通道和工具参数。
 *
 * @param approvalId 审批单标识
 * @param toolId     工具名
 * @param sessionId  会话
 * @param decision   APPROVED 或 REJECTED
 */
public record ApprovalDecisionView(String approvalId, String toolId, String sessionId, String decision) {
}
