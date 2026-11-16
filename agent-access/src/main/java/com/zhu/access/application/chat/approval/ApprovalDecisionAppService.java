package com.zhu.access.application.chat.approval;

import com.zhu.audit.api.approval.ApprovalDecision;
import com.zhu.audit.api.approval.ApprovalDecider;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.PendingAskFacade;
import com.zhu.runtime.api.PendingAskSummary;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/**
 * 接入层：审批决定编排。
 * 权威是运行层 {@link PendingAskFacade}（进程内仓 + 框架续跑）；旧审批表决定只作尽力同步，失败不回滚续跑。
 */
@Service
public class ApprovalDecisionAppService {

    private static final Logger log = Logger.getLogger(ApprovalDecisionAppService.class.getName());

    private final PendingAskFacade pendingAsks;

    private final ApprovalDecider decisions;

    /**
     * 只依赖 Facade 与旧表 Decider；不碰 ToolUseBlock。
     */
    public ApprovalDecisionAppService(PendingAskFacade pendingAsks, ApprovalDecider decisions) {
        this.pendingAsks = pendingAsks;
        this.decisions = decisions;
    }

    /**
     * 驱动续跑，并尽力把旧表同号行改成已决定。
     * 找不到进程内单据按 NOT_FOUND（不再「只改库」当成功）。
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
        boolean approved = decision == ApprovalDecision.APPROVED;
        PendingAskSummary summary =
                pendingAsks.decideAndResume(tenantId, userId, approvalId, approved, reason);
        if (summary == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "approval not found");
        }
        syncLegacyRow(approvalId, decision, userId, channel, reason);
        return summary;
    }

    /** 旧表可能已无此行（Middleware 停写后）；写失败只打日志。 */
    private void syncLegacyRow(
            String approvalId,
            ApprovalDecision decision,
            String approver,
            String channel,
            String reason) {
        try {
            decisions.decide(approvalId, decision, approver, channel, reason);
        } catch (RuntimeException error) {
            log.info("legacy approval row sync skipped for " + approvalId + ": "
                    + error.getClass().getSimpleName());
        }
    }
}
