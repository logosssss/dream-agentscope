package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.ApprovalDecisionRequest;
import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.api.ApprovalDecider;
import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.api.AuditEventRow;
import com.zhu.audit.api.AuditQuery;
import com.zhu.common.dto.ApiResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程上的审计查询和审批决定。读库和改决定都走审计模块，不读用户进程内存。
 */
@RestController
public class AdminAuditController {

    /** 本进程的通道固定为控制台，不接受调用方改通道。 */
    private static final String CHANNEL = "CONSOLE";

    private final AuditQuery audits;

    private final ApprovalDecider decisions;

    public AdminAuditController(AuditQuery audits, ApprovalDecider decisions) {
        this.audits = audits;
        this.decisions = decisions;
    }

    /**
     * 最近的审计事件。MySQL 关闭时为空列表。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/audits")
    public ApiResult<List<AuditEventRow>> audits(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(audits.recentEvents(limit));
    }

    /**
     * 待处理审批。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/approvals")
    public ApiResult<List<ApprovalRow>> approvals(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(audits.pendingApprovals(limit));
    }

    /** 操作人空白时与后台审计一致，记成 admin。 */
    private static final String DEFAULT_APPROVER = "admin";

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
