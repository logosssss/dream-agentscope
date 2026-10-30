package com.zhu.access.api.controller;

import com.zhu.access.api.dto.PendingApproval;
import com.zhu.access.application.ApprovalNotifyAppService;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 把当前用户还没取走的审批通知交出去。不查管理进程，也不读另一份 JVM 的内存。
 */
@RestController
public class ApprovalController {

    private final ApprovalNotifyAppService notifications;

    /** 列表按请求头里的租户和用户过滤。 */
    public ApprovalController(ApprovalNotifyAppService notifications) {
        this.notifications = notifications;
    }

    /**
     * 只返回这次请求对应的用户。没有用户标识时拒绝，避免把整份待推送列表交出去。
     * 取走之后仍留在本进程，刷新还能看到；进程重启会丢。
     */
    @GetMapping("/api/approvals/pending")
    public ApiResult<List<PendingApproval>> pending() {
        return ApiResult.ok(notifications.pendingFor(TenantContext.get(), UserContext.get()));
    }
}
