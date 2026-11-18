package com.zhu.access.application.chat.approval;

import com.zhu.audit.api.approval.ApprovalDecision;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.approval.PendingAskFacade;
import com.zhu.runtime.api.approval.PendingAskSummary;
import org.springframework.stereotype.Service;

/**
 * 接入层：审批决定编排。
 * 权威是运行层 {@link PendingAskFacade}（进程内仓 + 框架续跑）；旧审批表已硬删，不再同步。
 */
@Service
public class ApprovalDecisionAppService {

    private final PendingAskFacade pendingAsks;

    /**
     * 只依赖 Facade；不碰旧表 Decider / ToolUseBlock。
     */
    public ApprovalDecisionAppService(PendingAskFacade pendingAsks) {
        this.pendingAsks = pendingAsks;
    }

    /**
     * 驱动续跑。找不到进程内单据按 NOT_FOUND。
     */
    public PendingAskSummary decide(
            String tenantId,
            String userId,
            String approvalId,
            ApprovalDecision decision,
            String channel,
            String reason) {
        if (decision == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision is required");
        }
        // channel 由控制器固定；Facade 续跑不写库通道列
        boolean approved = decision == ApprovalDecision.APPROVED;
        PendingAskSummary summary =
                pendingAsks.decideAndResume(tenantId, userId, approvalId, approved, reason);
        if (summary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "approval not found");
        }
        return summary;
    }
}
