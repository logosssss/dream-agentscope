package com.zhu.access.application.chat;

import com.zhu.audit.api.approval.ApprovalNotifier;
import com.zhu.audit.api.approval.UserTodoCatalog;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 接入层订阅待确认事件。记下一条未发送通知，并给发起人开一条待办。
 * 不发 IM、邮件或短信，也不关闭待办。
 */
@Component
public class ApprovalNotificationListener {

    private static final Logger log = Logger.getLogger(ApprovalNotificationListener.class.getName());

    private final ObjectProvider<ApprovalNotifier> notifiers;

    private final ObjectProvider<UserTodoCatalog> todos;

    /** MySQL 关闭时两项都没有。事件仍然发出，这里直接跳过。 */
    public ApprovalNotificationListener(
            ObjectProvider<ApprovalNotifier> notifiers,
            ObjectProvider<UserTodoCatalog> todos) {
        this.notifiers = notifiers;
        this.todos = todos;
    }

    /**
     * 审批单已经落库。通知或待办写失败只记警告，不能把这次询问改成失败。
     * 两项分开接住，一条失败不挡住另一条。
     */
    @EventListener
    public void onRequested(ApprovalRequestedEvent event) {
        if (event == null) {
            return;
        }
        ApprovalNotifier notifier = notifiers.getIfAvailable();
        if (notifier != null) {
            try {
                notifier.pending(event.approvalId(), event.userId());
            } catch (RuntimeException ex) {
                log.warning("approval notification record failed");
            }
        }
        UserTodoCatalog catalog = todos.getIfAvailable();
        if (catalog == null) {
            return;
        }
        try {
            catalog.openApproval(event.userId(), event.approvalId(), event.toolId());
        } catch (RuntimeException ex) {
            log.warning("approval todo record failed");
        }
    }
}
