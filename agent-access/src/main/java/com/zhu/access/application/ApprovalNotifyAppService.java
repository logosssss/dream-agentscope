package com.zhu.access.application;

import com.zhu.access.api.dto.PendingApproval;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 审批通知。订阅运行层发出的事件，按用户交给接入接口。
 * 列表只在本进程内存里，重启会丢，也不等于管理进程读到的 MySQL 单据。
 */
@Service
public class ApprovalNotifyAppService {

    private final List<ApprovalRequestedEvent> pending = new CopyOnWriteArrayList<>();

    /**
     * 收到审批请求后暂存。同步监听，避免事件在列表写入前就结束。
     */
    @EventListener
    public void onApproval(ApprovalRequestedEvent event) {
        if (event != null) {
            pending.add(event);
        }
    }

    /** 还没交出去的全部通知。返回副本，供本进程内查看。 */
    public List<ApprovalRequestedEvent> pending() {
        return List.copyOf(pending);
    }

    /**
     * 只留下这个用户、这个租户的通知。用户为空时拒绝，不把别人的单据交出去。
     * 租户空白与 null 视为同一个“没有租户”。取走后不删除，刷新还能看到。
     */
    public List<PendingApproval> pendingFor(String tenantId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        String tenant = blankToNull(tenantId);
        List<PendingApproval> matched = new ArrayList<>();
        for (ApprovalRequestedEvent event : pending) {
            if (userId.equals(event.userId()) && same(tenant, blankToNull(event.tenantId()))) {
                matched.add(new PendingApproval(event.approvalId(), event.toolId()));
            }
        }
        return List.copyOf(matched);
    }

    /** 丢掉本进程里还没交出去的通知。不改审批单本身的决定。 */
    public void clear() {
        pending.clear();
    }

    /** 空白不当成一个租户名。 */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /** 两个都没有租户时也算同一侧。 */
    private static boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
