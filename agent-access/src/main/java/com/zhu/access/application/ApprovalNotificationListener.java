package com.zhu.access.application;

import com.zhu.audit.api.ApprovalNotifier;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 接入层订阅待确认事件。只记下一条未发送通知，不发 IM、邮件或短信。
 */
@Component
public class ApprovalNotificationListener {

    private static final Logger log = Logger.getLogger(ApprovalNotificationListener.class.getName());

    private final ObjectProvider<ApprovalNotifier> notifiers;

    /** MySQL 关闭时没有通知写入。事件仍然发出，这里直接跳过。 */
    public ApprovalNotificationListener(ObjectProvider<ApprovalNotifier> notifiers) {
        this.notifiers = notifiers;
    }

    /**
     * 审批单已经落库。通知写失败只记警告，不能把这次询问改成失败。
     */
    @EventListener
    public void onRequested(ApprovalRequestedEvent event) {
        if (event == null) {
            return;
        }
        ApprovalNotifier notifier = notifiers.getIfAvailable();
        if (notifier == null) {
            return;
        }
        try {
            notifier.pending(event.approvalId(), event.userId());
        } catch (RuntimeException ex) {
            log.warning("approval notification record failed");
        }
    }
}
