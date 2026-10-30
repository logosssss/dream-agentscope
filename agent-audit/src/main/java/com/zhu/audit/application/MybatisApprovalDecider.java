package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.api.ApprovalDecider;
import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.infrastructure.persistence.ApprovalRecordMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把 PENDING 更新成通过或拒绝。只在 MySQL 打开时创建。
 * 不写 executed_at 和 result，执行回写留给内核。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MybatisApprovalDecider implements ApprovalDecider {

    private final ApprovalRecordMapper approvals;

    public MybatisApprovalDecider(ApprovalRecordMapper approvals) {
        this.approvals = approvals;
    }

    /**
     * 先读再按 PENDING 更新。更新行数不是 1 说明并发下已被别人决定，不再覆盖。
     */
    @Override
    public ApprovalRow decide(String approvalId, ApprovalDecision decision, String approver, String channel, String reason) {
        if (decision == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision must be APPROVED or REJECTED");
        }
        String storedReason = ApprovalDecisionRules.reason(approvalId, approver, channel, reason);
        ApprovalRecord existing = approvals.findByApprovalId(approvalId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "approval not found");
        }
        if (!"PENDING".equals(existing.getDecision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "approval already decided");
        }
        LocalDateTime decidedAt = LocalDateTime.now();
        int updated = approvals.decidePending(approvalId, decision.name(), approver, channel, storedReason, decidedAt);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "approval already decided");
        }
        return toRow(existing, decision.name(), approver, channel, storedReason, decidedAt);
    }

    /** 用更新前读到的身份字段，加上这次写入的决定。 */
    private static ApprovalRow toRow(
            ApprovalRecord existing,
            String decision,
            String approver,
            String channel,
            String reason,
            LocalDateTime decidedAt) {
        return new ApprovalRow(
                existing.getApprovalId(),
                existing.getUserId(),
                existing.getSessionId(),
                existing.getToolName(),
                decision,
                existing.getRequestedAt(),
                approver,
                channel,
                reason,
                decidedAt);
    }
}
