package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.api.ApprovalDecider;
import com.zhu.audit.api.ApprovalRow;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MySQL 关闭时的决定口。不改本进程内存里的待审批，因为那份数据不是共享的。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class EmptyApprovalDecider implements ApprovalDecider {

    /**
     * 入参先按列宽检查。通过检查后仍然找不到共享单据。
     */
    @Override
    public ApprovalRow decide(String approvalId, ApprovalDecision decision, String approver, String channel, String reason) {
        if (decision == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision must be APPROVED or REJECTED");
        }
        ApprovalDecisionRules.reason(approvalId, approver, channel, reason);
        throw new BusinessException(ErrorCode.NOT_FOUND, "approval not found");
    }
}
