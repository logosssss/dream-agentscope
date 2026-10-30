package com.zhu.access.application;

import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 审批通知。订阅运行层发出的事件，接入层负责推送。当前没有 IM 或 SSE 通道，先收进待推送列表。
 */
@Service
public class ApprovalNotifyAppService {

    private final List<ApprovalRequestedEvent> pending = new CopyOnWriteArrayList<>();

    /**
     * 收到审批请求后暂存。同步监听，避免事件在列表写入前就结束。
     */
    @EventListener
    public void onApproval(ApprovalRequestedEvent event) {
        pending.add(event);
    }

    /** 还没推送出去的审批。返回副本。 */
    public List<ApprovalRequestedEvent> pending() {
        return List.copyOf(pending);
    }
}
