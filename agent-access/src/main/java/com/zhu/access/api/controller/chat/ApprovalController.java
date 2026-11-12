package com.zhu.access.api.controller.chat;

import com.zhu.access.api.dto.chat.ApprovalDecisionRequest;
import com.zhu.access.api.dto.chat.ApprovalDecisionView;
import com.zhu.access.api.dto.chat.ApprovalDetail;
import com.zhu.access.api.dto.chat.PendingApproval;
import com.zhu.access.application.auth.gate.AccessAccountGate;
import com.zhu.access.application.chat.approval.ApprovalDecisionAppService;
import com.zhu.access.application.chat.approval.PendingAsk;
import com.zhu.access.application.chat.approval.PendingAskStore;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.audit.api.approval.ApprovalDecision;
import com.zhu.audit.api.approval.ApprovalRow;
import com.zhu.audit.api.event.AuditQuery;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待确认列表与人批决定。列表主数据源是 Gate ASK 进程内登记；决定后驱动框架续跑。
 * 旧审批表只读查询仍可看历史行；新 ASK 不再依赖落单。
 */
@RestController
public class ApprovalController {

    /** 用户自批通道固定为页面，不接受调用方改通道。 */
    private static final String CHANNEL = AccessChannel.WEB.name();

    /** 审批单标识列宽。超长按请求错误，避免把截断后的单号当成另一张单。 */
    private static final int APPROVAL_ID_LIMIT = 64;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AuditQuery audits;

    private final PendingAskStore pendingAsks;

    private final ApprovalDecisionAppService decisions;

    private final AccessAccountGate accounts;

    /** 列表和决定都按请求头里的租户和用户。先过账号门。 */
    public ApprovalController(
            AuditQuery audits,
            PendingAskStore pendingAsks,
            ApprovalDecisionAppService decisions,
            AccessAccountGate accounts) {
        this.audits = audits;
        this.pendingAsks = pendingAsks;
        this.decisions = decisions;
        this.accounts = accounts;
    }

    /**
     * 只返回这次请求对应用户的进程内待确认（Gate ASKING）。
     * 不再以审批表 PENDING 为主；表内存量另走详情只读。
     */
    @GetMapping("/api/approvals/pending")
    public ApiResult<List<PendingApproval>> pending() {
        accounts.requireActive();
        String userId = UserContext.get();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        List<PendingAsk> rows = pendingAsks.pendingFor(tenantId, userId, 50);
        List<PendingApproval> result = new ArrayList<>(rows.size());
        for (PendingAsk row : rows) {
            result.add(new PendingApproval(row.approvalId(), row.toolName(), row.sessionId()));
        }
        return ApiResult.ok(result);
    }

    /**
     * 先查进程内待确认；没有再读旧表本人本租户行（含已决定历史）。
     * 不读工具参数和执行结果。
     */
    @GetMapping("/api/approvals/{approvalId}")
    public ApiResult<ApprovalDetail> get(@PathVariable String approvalId) {
        accounts.requireActive();
        if (approvalId == null || approvalId.isBlank() || approvalId.length() > APPROVAL_ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "approval is invalid");
        }
        String userId = UserContext.get();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        PendingAsk live = pendingAsks.ownedOne(tenantId, userId, approvalId);
        if (live != null) {
            return ApiResult.ok(new ApprovalDetail(
                    live.approvalId(),
                    live.toolName(),
                    live.sessionId(),
                    "PENDING",
                    "",
                    "",
                    null));
        }
        ApprovalRow owned = audits.ownedOne(tenantId, userId, approvalId);
        if (owned == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "approval not found");
        }
        String requested = owned.requestedAt() == null ? "" : TIME.format(owned.requestedAt());
        String decided = owned.decidedAt() == null ? "" : TIME.format(owned.decidedAt());
        return ApiResult.ok(new ApprovalDetail(
                owned.approvalId(),
                owned.toolName(),
                owned.sessionId(),
                owned.decision(),
                requested,
                decided,
                owned.reason()));
    }

    /**
     * 本人本租户决定后驱动框架续跑（ConfirmResult）。
     * 找不到进程内单据按找不到；通道固定 WEB。
     */
    @PostMapping("/api/approvals/{approvalId}/decision")
    public ApiResult<ApprovalDecisionView> decide(
            @PathVariable String approvalId,
            @RequestBody ApprovalDecisionRequest request) {
        accounts.requireActive();
        if (approvalId == null || approvalId.isBlank() || approvalId.length() > APPROVAL_ID_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "approval is invalid");
        }
        String userId = UserContext.get();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        ApprovalDecisionRequest body = request == null ? new ApprovalDecisionRequest(null, null) : request;
        ApprovalDecision parsed = ApprovalDecision.parse(body.decision());
        PendingAsk decided = decisions.decide(
                tenantId,
                userId,
                approvalId,
                parsed,
                CHANNEL,
                body.reason());
        return ApiResult.ok(new ApprovalDecisionView(
                decided.approvalId(),
                decided.toolName(),
                decided.sessionId(),
                parsed.name()));
    }
}
