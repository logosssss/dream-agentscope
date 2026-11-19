package com.zhu.access.api.controller.account;

import com.zhu.access.application.auth.gate.AccessAccountGate;
import com.zhu.access.api.approval.ApprovalNotice;
import com.zhu.access.api.approval.ApprovalNoticeDetail;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户查看通知。审批通知表已硬删；列表固定空，详情固定找不到。
 * 路由保留，避免前端断链；见 docs/approval-hard-delete-落地.md。
 */
@RestController
public class NotificationController {

    private final AccessAccountGate accounts;

    /** 仍过账号门；不再依赖 Notifier / 表。 */
    public NotificationController(AccessAccountGate accounts) {
        this.accounts = accounts;
    }

    /**
     * 审批通知已退役：始终空列表。条数参数保留但不使用。
     */
    @GetMapping("/api/notifications")
    public ApiResult<List<ApprovalNotice>> list(@RequestParam(defaultValue = "50") int limit) {
        accounts.requireActive();
        return ApiResult.ok(List.of());
    }

    /**
     * 表已删除：任意主键按找不到。
     */
    @GetMapping("/api/notifications/{notificationId}")
    public ApiResult<ApprovalNoticeDetail> get(@PathVariable Long notificationId) {
        accounts.requireActive();
        throw new BusinessException(ErrorCode.NOT_FOUND, "notification not found");
    }
}
