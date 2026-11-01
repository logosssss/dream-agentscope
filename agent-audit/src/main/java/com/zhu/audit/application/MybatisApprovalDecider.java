package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.api.ApprovalDecider;
import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.api.UserTodoCatalog;
import com.zhu.audit.infrastructure.persistence.ApprovalRecordMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把 PENDING 更新成通过或拒绝。只在 MySQL 打开时创建。
 * 不写 executed_at 和 result。执行回写在用户进程放行工具之后。
 * 决定成功后再关对应待办，关闭失败不改已经写下的决定。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MybatisApprovalDecider implements ApprovalDecider {

    private static final Logger log = Logger.getLogger(MybatisApprovalDecider.class.getName());

    private final ApprovalRecordMapper approvals;

    private final UserTodoCatalog todos;

    /** 待办口和审批表一起注入。管理进程也要能关待办。 */
    public MybatisApprovalDecider(ApprovalRecordMapper approvals, UserTodoCatalog todos) {
        this.approvals = approvals;
        this.todos = todos;
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
        closeTodo(approvalId, decision);
        return toRow(existing, decision.name(), approver, channel, storedReason, decidedAt);
    }

    /**
     * 通过改 DONE，拒绝改 CANCELLED。没有待办时更新 0 行。
     * 这里失败只打日志，避免决定已经写下后又被当成失败。
     */
    private void closeTodo(String approvalId, ApprovalDecision decision) {
        try {
            todos.closeApproval(approvalId, decision == ApprovalDecision.APPROVED ? "DONE" : "CANCELLED");
        } catch (RuntimeException ex) {
            log.warning("approval todo close failed");
        }
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
