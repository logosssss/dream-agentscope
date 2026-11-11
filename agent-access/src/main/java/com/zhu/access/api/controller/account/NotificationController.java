package com.zhu.access.api.controller.account;

import com.zhu.access.application.auth.AccessAccountGate;
import com.zhu.audit.api.ApprovalNotice;
import com.zhu.audit.api.ApprovalNoticeDetail;
import com.zhu.audit.api.ApprovalNotifier;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户查看审批通知。不发送消息，也不改发送状态。
 */
@RestController
public class NotificationController {

    private final ObjectProvider<ApprovalNotifier> notifiers;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public NotificationController(ObjectProvider<ApprovalNotifier> notifiers, AccessAccountGate accounts) {
        this.notifiers = notifiers;
        this.accounts = accounts;
    }

    /**
     * 只返回这次请求头里的用户。条数默认 50，最多 100。
     * 表没有租户列，不按租户再滤，也不返回别人的行。
     */
    @GetMapping("/api/notifications")
    public ApiResult<List<ApprovalNotice>> list(@RequestParam(defaultValue = "50") int limit) {
        accounts.requireActive();
        ApprovalNotifier notifier = notifiers.getIfAvailable();
        if (notifier == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "notification is unavailable");
        }
        return ApiResult.ok(notifier.owned(UserContext.get(), limit));
    }

    /**
     * 别人的主键按找不到处理。含已发送或失败的行，不带回错误和发送时间。
     */
    @GetMapping("/api/notifications/{notificationId}")
    public ApiResult<ApprovalNoticeDetail> get(@PathVariable Long notificationId) {
        accounts.requireActive();
        ApprovalNotifier notifier = notifiers.getIfAvailable();
        if (notifier == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "notification is unavailable");
        }
        return ApiResult.ok(notifier.ownedOne(UserContext.get(), notificationId));
    }
}
