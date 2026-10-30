package com.zhu.audit.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;

/**
 * 审批决定的入参约束。长度跟 approval_record 的列宽对齐，超长在进库前拒绝。
 */
final class ApprovalDecisionRules {

    private ApprovalDecisionRules() {
    }

    /**
     * 单号、审批人和通道必填。意见留空写成 null，不把空白存进可空列。
     */
    static String reason(String approvalId, String approver, String channel, String reason) {
        if (blank(approvalId) || approvalId.length() > 64
                || blank(approver) || approver.length() > 128
                || blank(channel) || channel.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "approvalId, approver and channel are required");
        }
        if (reason == null || reason.isBlank()) {
            return null;
        }
        if (reason.length() > 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "reason is too long");
        }
        return reason;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
