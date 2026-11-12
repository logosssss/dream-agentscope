package com.zhu.admin.api.controller.approval;

import com.zhu.admin.api.dto.approval.ApprovalDecisionRequest;
import com.zhu.audit.api.approval.ApprovalDecision;
import com.zhu.audit.api.approval.ApprovalDecider;
import com.zhu.audit.api.approval.ApprovalRow;
import com.zhu.audit.api.event.AuditQuery;
import com.zhu.common.dto.ApiResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程上的审批列表与决定（过渡保留）。
 * 只读写旧 {@code approval_record}，不驱动框架续跑；新 ASK 活单在用户进程 PendingAskStore。
 * 表硬删另块。见 {@code docs/permission-gate-落地.md}。
 */
@RestController
public class AdminApprovalController {

    /** 本进程的通道固定为控制台，不接受调用方改通道。 */
    private static final String CHANNEL = "CONSOLE";

    /** 操作人空白时与后台审计一致，记成 admin。 */
    private static final String DEFAULT_APPROVER = "admin";

    private final AuditQuery audits;

    private final ApprovalDecider decisions;

    /** 列表走查询口，决定走决定口。 */
    public AdminApprovalController(AuditQuery audits, ApprovalDecider decisions) {
        this.audits = audits;
        this.decisions = decisions;
    }

    /**
     * 待处理审批。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/approvals")
    public ApiResult<List<ApprovalRow>> approvals(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(audits.pendingApprovals(limit));
    }

    /**
     * 把一张待审批改成通过或拒绝。关掉对应待办，仍不触发工具执行。
     * 审批人可空，空则按 admin。单据不存在或已经决定过时，由决定口返回失败。
     */
    @PostMapping("/api/admin/approvals/{approvalId}/decision")
    public ApiResult<ApprovalRow> decide(
            @PathVariable String approvalId,
            @RequestBody ApprovalDecisionRequest request) {
        ApprovalDecisionRequest body = request == null
                ? new ApprovalDecisionRequest(null, null, null)
                : request;
        String approver = body.approver() == null || body.approver().isBlank()
                ? DEFAULT_APPROVER
                : body.approver().strip();
        return ApiResult.ok(decisions.decide(
                approvalId,
                ApprovalDecision.parse(body.decision()),
                approver,
                CHANNEL,
                body.reason()));
    }
}
