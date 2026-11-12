package com.zhu.access.application.chat.approval;

import com.zhu.audit.api.approval.ApprovalDecision;
import com.zhu.audit.api.approval.ApprovalDecider;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/**
 * 接入层：审批决定编排。
 * 权威是进程内 {@link PendingAskStore} + 框架续跑；旧审批表决定只作尽力同步，失败不回滚续跑。
 */
@Service
public class ApprovalDecisionAppService {

    private static final Logger log = Logger.getLogger(ApprovalDecisionAppService.class.getName());

    private final PendingAskStore pendingAsks;

    private final AgentPermissionResume resumes;

    private final ApprovalDecider decisions;

    public ApprovalDecisionAppService(
            PendingAskStore pendingAsks,
            AgentPermissionResume resumes,
            ApprovalDecider decisions) {
        this.pendingAsks = pendingAsks;
        this.resumes = resumes;
        this.decisions = decisions;
    }

    /**
     * 取出待确认、驱动续跑，并尽力把旧表同号行改成已决定。
     * 找不到进程内单据按 NOT_FOUND（不再「只改库」当成功）。
     */
    public PendingAsk decide(
            String tenantId,
            String userId,
            String approvalId,
            ApprovalDecision decision,
            String channel,
            String reason) {
        if (decision == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision is required");
        }
        PendingAsk ask = pendingAsks.takeOwned(tenantId, userId, approvalId);
        if (ask == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "approval not found");
        }
        boolean approved = decision == ApprovalDecision.APPROVED;
        resumes.resume(ask, approved, reason);
        syncLegacyRow(approvalId, decision, userId, channel, reason);
        return ask;
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
