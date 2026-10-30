package com.zhu.audit.infrastructure.persistence;

import com.zhu.audit.api.ApprovalRecorder;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 用 BaseMapper 写审批单。只在 MySQL 打开时创建，和进程内实现互斥。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MybatisApprovalRecorder implements ApprovalRecorder {

    private final ApprovalRecordMapper approvals;

    public MybatisApprovalRecorder(ApprovalRecordMapper approvals) {
        this.approvals = approvals;
    }

    /**
     * 新单的决定固定为 PENDING。会话为空时写成空串，满足非空列。
     * 工具参数不入库。
     */
    @Override
    public void requested(String approvalId, String toolId, String userId, String sessionId) {
        ApprovalRecord row = new ApprovalRecord();
        row.setApprovalId(approvalId);
        row.setUserId(userId == null ? "" : userId);
        row.setSessionId(sessionId == null ? "" : sessionId);
        row.setToolName(toolId);
        row.setDecision("PENDING");
        approvals.insert(row);
    }
}
