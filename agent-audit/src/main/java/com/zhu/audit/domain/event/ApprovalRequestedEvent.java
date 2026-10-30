package com.zhu.audit.domain.event;

public record ApprovalRequestedEvent(
        String approvalId,
        String toolId,
        String tenantId,
        String userId
) {
}
