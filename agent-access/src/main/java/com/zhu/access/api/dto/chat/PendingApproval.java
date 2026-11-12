package com.zhu.access.api.dto.chat;

/**
 * 交给当前用户看的一条待确认。只有单号、工具名和会话，不带工具参数，也不带别人的身份。
 *
 * @param approvalId 审批单标识
 * @param toolId     待确认的工具
 * @param sessionId  发起时的会话，批准后重发仍绑同一会话
 */
public record PendingApproval(String approvalId, String toolId, String sessionId) {
}
