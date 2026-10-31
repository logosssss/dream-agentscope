package com.zhu.audit.infrastructure.persistence;

import com.zhu.audit.api.ApprovalRecorder;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 用 BaseMapper 写审批单。只在 MySQL 打开时创建。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MybatisApprovalRecorder implements ApprovalRecorder {

    private final ApprovalRecordMapper approvals;

    public MybatisApprovalRecorder(ApprovalRecordMapper approvals) {
        this.approvals = approvals;
    }

    /**
     * 新单的决定固定为 PENDING。会话和租户为空时写成空串，满足非空列。
     * 工具参数不入库。
     */
    @Override
    public void requested(String approvalId, String toolId, String userId, String sessionId, String tenantId) {
        ApprovalRecord row = new ApprovalRecord();
        row.setApprovalId(approvalId);
        row.setTenantId(tenantId == null || tenantId.isBlank() ? "" : tenantId);
        row.setUserId(userId == null ? "" : userId);
        row.setSessionId(sessionId == null ? "" : sessionId);
        row.setToolName(toolId);
        row.setDecision("PENDING");
        approvals.insert(row);
    }

    /**
     * 工具名为空时不占。身份空值写成空串，和申请时的落库一致。
     * 更新没抢到行就返回 null，调用方会重新走询问，不会放行。
     */
    @Override
    public String claimApproved(String tenantId, String userId, String sessionId, String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return null;
        }
        ApprovalRecord row = approvals.findApprovedUnused(text(tenantId), text(userId), text(sessionId), toolId);
        if (row == null || row.getApprovalId() == null) {
            return null;
        }
        int updated = approvals.claimExecution(row.getApprovalId(), LocalDateTime.now());
        return updated == 1 ? row.getApprovalId() : null;
    }

    /**
     * 结果固定成状态 JSON。未知状态记成 FAILED，避免把调用方的原文写进 JSON 列。
     */
    @Override
    public void executed(String approvalId, String status) {
        if (approvalId == null || approvalId.isBlank()) {
            return;
        }
        approvals.writeExecutionResult(approvalId, "{\"status\":\"" + known(status) + "\"}");
    }

    /** 空身份和申请时一样落成空串。 */
    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** 只保留三种执行状态。 */
    private static String known(String status) {
        if ("SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            return status;
        }
        return "FAILED";
    }
}
