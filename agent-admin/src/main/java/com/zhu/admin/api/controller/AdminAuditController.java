package com.zhu.admin.api.controller;

import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.api.AuditEventRow;
import com.zhu.audit.api.AuditQuery;
import com.zhu.common.dto.ApiResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程上的审计和审批查询。只读已经落库的数据，不读用户进程内存。
 */
@RestController
public class AdminAuditController {

    private final AuditQuery audits;

    public AdminAuditController(AuditQuery audits) {
        this.audits = audits;
    }

    /**
     * 最近的审计事件。MySQL 关闭时为空列表。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/audits")
    public ApiResult<List<AuditEventRow>> audits(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(audits.recentEvents(limit));
    }

    /**
     * 待处理审批。MySQL 关闭时为空列表。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/approvals")
    public ApiResult<List<ApprovalRow>> approvals(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(audits.pendingApprovals(limit));
    }
}
