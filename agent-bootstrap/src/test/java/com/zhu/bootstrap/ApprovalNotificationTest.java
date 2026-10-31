package com.zhu.bootstrap;

import com.zhu.access.application.ApprovalNotificationListener;
import com.zhu.audit.api.ApprovalNotifier;
import com.zhu.audit.api.UserTodoCatalog;
import com.zhu.audit.application.MysqlApprovalNotifier;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import com.zhu.audit.infrastructure.persistence.ApprovalNotificationMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalNotification;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 待确认只记一条未发送通知。不连接 MySQL，也不发消息。
 */
class ApprovalNotificationTest {

    /** 有写入实现时记下发起人。写入抛错不打断这次询问。没有实现时跳过。 */
    @Test
    void listenerRecordsPendingAndIgnoresFailure() {
        ApprovalNotifier notifier = mock(ApprovalNotifier.class);
        ApprovalNotificationListener listener = listener(notifier);
        listener.onRequested(new ApprovalRequestedEvent("ap-1", "shell", "t", "u"));
        verify(notifier).pending("ap-1", "u");

        doThrow(new IllegalStateException("down")).when(notifier).pending("ap-1", "u");
        assertThatCode(() -> listener.onRequested(new ApprovalRequestedEvent("ap-1", "shell", "t", "u")))
                .doesNotThrowAnyException();

        ApprovalNotificationListener absent = listener(null);
        absent.onRequested(new ApprovalRequestedEvent("ap-1", "shell", "t", "u"));
    }

    /** 通道固定 WEB，状态是 PENDING。没有接收人时不插入。 */
    @Test
    void writerInsertsPendingWebNotice() {
        ApprovalNotificationMapper notifications = mock(ApprovalNotificationMapper.class);
        MysqlApprovalNotifier notifier = new MysqlApprovalNotifier(notifications);
        notifier.pending("ap-1", " ");
        verifyNoInteractions(notifications);

        notifier.pending("ap-1", "u");
        ArgumentCaptor<ApprovalNotification> saved = ArgumentCaptor.forClass(ApprovalNotification.class);
        verify(notifications).insert(saved.capture());
        ApprovalNotification row = saved.getValue();
        assertThat(row.getApprovalId()).isEqualTo("ap-1");
        assertThat(row.getReceiverId()).isEqualTo("u");
        assertThat(row.getChannel()).isEqualTo("WEB");
        assertThat(row.getSendStatus()).isEqualTo("PENDING");
        assertThat(row.getRetryCount()).isZero();
    }

    /** 测试里用固定的查找结果，避免把 ObjectProvider 的其余方法都实现一遍。 */
    private static ApprovalNotificationListener listener(ApprovalNotifier notifier) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalNotifier> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(notifier);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserTodoCatalog> todos = mock(ObjectProvider.class);
        when(todos.getIfAvailable()).thenReturn(null);
        return new ApprovalNotificationListener(providers, todos);
    }
}
