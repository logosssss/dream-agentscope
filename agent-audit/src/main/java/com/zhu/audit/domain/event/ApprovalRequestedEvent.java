package com.zhu.audit.domain.event;

/**
 * 需要人工确认时发出的事件（软退役）。
 * Middleware 已停发；Gate ASK 改由接入层 {@code PendingAskStore} 登记。
 * 监听器与表暂留，硬删另块。见 {@code docs/permission-gate-落地.md}。
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
