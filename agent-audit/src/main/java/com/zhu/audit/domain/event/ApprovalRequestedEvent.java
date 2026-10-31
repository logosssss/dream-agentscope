package com.zhu.audit.domain.event;

/**
 * 需要人工确认时发出的事件。运行层发布，接入层订阅后只记一条未发送通知。
 * 这次不推送。运行层因此不必依赖接入层。
 *
 * @param approvalId 审批单标识
 * @param toolId     待确认的工具
 * @param tenantId   租户
 * @param userId     发起人
 */
public record ApprovalRequestedEvent(
        String approvalId,
        String toolId,
        String tenantId,
        String userId
) {
}
