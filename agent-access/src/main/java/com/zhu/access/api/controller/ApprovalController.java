package com.zhu.access.api.controller;

import com.zhu.access.api.dto.ApprovalDecisionRequest;
import com.zhu.access.api.dto.ApprovalDecisionView;
import com.zhu.access.api.dto.PendingApproval;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.api.ApprovalDecider;
import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.api.AuditQuery;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 把当前用户的待确认从 MySQL 交出去，并接受本人决定。不读进程内存，也不执行工具。
 */
@RestController
public class ApprovalController {

    /** 用户自批通道固定为页面，不接受调用方改通道。 */
    private static final String CHANNEL = AccessChannel.WEB.name();

    private final AuditQuery audits;

    private final ApprovalDecider decisions;

    private final AccessAccountGate accounts;

    /** 列表和决定都按请求头里的租户和用户查库。先过账号门。 */
    public ApprovalController(AuditQuery audits, ApprovalDecider decisions, AccessAccountGate accounts) {
        this.audits = audits;
        this.decisions = decisions;
        this.accounts = accounts;
    }

    /**
     * 只返回这次请求对应的用户。没有用户或账号不可用时拒绝，避免把别人的单据交出去。
     * 数据在 MySQL。进程重启后仍然能查到未决定的单据。
     */
    @GetMapping("/api/approvals/pending")
    public ApiResult<List<PendingApproval>> pending() {
        accounts.requireActive();
        String userId = UserContext.get();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        List<ApprovalRow> rows = audits.pendingFor(tenantId, userId, 50);
        List<PendingApproval> result = new ArrayList<>(rows.size());
        for (ApprovalRow row : rows) {
            result.add(new PendingApproval(row.approvalId(), row.toolName(), row.sessionId()));
        }
        return ApiResult.ok(result);
    }

    /**
     * 把本人本租户的一张待确认改成通过或拒绝。审批人取当前用户，通道固定 WEB。
     * 别人的或找不到的按找不到处理。关掉对应待办，仍不触发工具执行。
     */
    @PostMapping("/api/approvals/{approvalId}/decision")
    public ApiResult<ApprovalDecisionView> decide(
            @PathVariable String approvalId,
            @RequestBody ApprovalDecisionRequest request) {
        accounts.requireActive();
        String userId = UserContext.get();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        ApprovalRow owned = audits.ownedOne(tenantId, userId, approvalId);
        if (owned == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "approval not found");
        }
        ApprovalDecisionRequest body = request == null ? new ApprovalDecisionRequest(null, null) : request;
        ApprovalRow decided = decisions.decide(
                approvalId,
                ApprovalDecision.parse(body.decision()),
                userId,
                CHANNEL,
                body.reason());
        return ApiResult.ok(new ApprovalDecisionView(
                decided.approvalId(),
                decided.toolName(),
                decided.sessionId(),
                decided.decision()));
    }
}
