package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.api.DeletionRequestDecider;
import com.zhu.audit.api.DeletionRequestSummary;
import com.zhu.audit.infrastructure.persistence.DataDeletionRequestMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataDeletionRequest;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把 PENDING 的删除请求改成通过或拒绝。只在 MySQL 打开时创建。不执行删除。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlDeletionRequestDecider implements DeletionRequestDecider {

    private final DataDeletionRequestMapper requests;

    /** 先读再按 PENDING 更新，避免覆盖已经决定过的请求。 */
    public MysqlDeletionRequestDecider(DataDeletionRequestMapper requests) {
        this.requests = requests;
    }

    /**
     * 决定不能为空。更新行数不是 1 说明并发下已被别人决定。
     * 不写执行时间和删除结果。
     */
    @Override
    public DeletionRequestSummary decide(String requestId, ApprovalDecision decision) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "deletion request is invalid");
        }
        if (decision == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision must be APPROVED or REJECTED");
        }
        DataDeletionRequest existing = requests.findByRequestId(requestId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "deletion request not found");
        }
        if (!"PENDING".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "deletion request already decided");
        }
        int updated = requests.decidePending(requestId, decision.name());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "deletion request already decided");
        }
        return new DeletionRequestSummary(
                existing.getRequestId(),
                existing.getUserId(),
                existing.getTenantId(),
                existing.getDataType(),
                decision.name(),
                existing.getReason());
    }
}
