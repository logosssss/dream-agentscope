package com.zhu.access.api.controller;

import com.zhu.access.api.dto.PendingApproval;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 把当前用户的待确认从 MySQL 交出去。不读进程内存。
 */
@RestController
public class ApprovalController {

    private final AuditQuery audits;

    /** 列表按请求头里的租户和用户查库。 */
    public ApprovalController(AuditQuery audits) {
        this.audits = audits;
    }

    /**
     * 只返回这次请求对应的用户。没有用户标识时拒绝，避免把别人的单据交出去。
     * 数据在 MySQL。进程重启后仍然能查到未决定的单据。
     */
    @GetMapping("/api/approvals/pending")
    public ApiResult<List<PendingApproval>> pending() {
        String userId = UserContext.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        List<ApprovalRow> rows = audits.pendingFor(tenantId, userId, 50);
        List<PendingApproval> result = new ArrayList<>(rows.size());
        for (ApprovalRow row : rows) {
            result.add(new PendingApproval(row.approvalId(), row.toolName()));
        }
        return ApiResult.ok(result);
    }
}
