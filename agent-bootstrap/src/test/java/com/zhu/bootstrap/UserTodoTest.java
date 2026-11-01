package com.zhu.bootstrap;

import com.zhu.access.application.ApprovalNotificationListener;
import com.zhu.audit.api.ApprovalNotifier;
import com.zhu.audit.api.UserTodoCatalog;
import com.zhu.audit.application.MysqlUserTodoCatalog;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import com.zhu.audit.infrastructure.persistence.UserTodoMapper;
import com.zhu.audit.infrastructure.persistence.entity.UserTodo;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 待确认给发起人开一条待办。决定后再关掉。不连接 MySQL。
 */
class UserTodoTest {

    /** 事件带上工具名。重复单号不再抛出。空用户不插入。 */
    @Test
    void openApprovalOncePerUserAndRequest() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);
        catalog.openApproval(" ", "ap-1", "shell");
        verifyNoInteractions(todos);

        catalog.openApproval("u", "ap-1", "shell");
        ArgumentCaptor<UserTodo> saved = ArgumentCaptor.forClass(UserTodo.class);
        verify(todos).insert(saved.capture());
        UserTodo row = saved.getValue();
        assertThat(row.getUserId()).isEqualTo("u");
        assertThat(row.getTodoType()).isEqualTo("APPROVAL");
        assertThat(row.getRefId()).isEqualTo("ap-1");
        assertThat(row.getTitle()).isEqualTo("shell");
        assertThat(row.getStatus()).isEqualTo("OPEN");

        when(todos.insert(any(UserTodo.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatCode(() -> catalog.openApproval("u", "ap-1", "shell")).doesNotThrowAnyException();
    }

    /** 列表只映射还开着的一行。空用户不查库。 */
    @Test
    void listRequiresAUser() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);
        assertThatCode(() -> catalog.openApprovals(" ", 50))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoInteractions(todos);

        UserTodo row = new UserTodo();
        row.setId(4L);
        row.setTodoType("APPROVAL");
        row.setRefId("ap-1");
        row.setTitle("shell");
        row.setStatus("OPEN");
        when(todos.openApprovals("u", 50)).thenReturn(List.of(row));
        assertThat(catalog.openApprovals("u", 50)).singleElement().satisfies(saved -> {
            assertThat(saved.refId()).isEqualTo("ap-1");
            assertThat(saved.title()).isEqualTo("shell");
            assertThat(saved.status()).isEqualTo("OPEN");
        });
    }

    /** 订阅方把工具名交给待办。待办写失败不打断通知。 */
    @Test
    void listenerOpensATodo() {
        UserTodoCatalog catalog = mock(UserTodoCatalog.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalNotifier> notifiers = mock(ObjectProvider.class);
        when(notifiers.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserTodoCatalog> todos = mock(ObjectProvider.class);
        when(todos.getIfAvailable()).thenReturn(catalog);
        ApprovalNotificationListener listener = new ApprovalNotificationListener(notifiers, todos);

        listener.onRequested(new ApprovalRequestedEvent("ap-1", "shell", "t", "u"));
        verify(catalog).openApproval("u", "ap-1", "shell");
    }

    /** 只关还开着的审批待办。空单号或未登记状态不碰 Mapper。 */
    @Test
    void closeApprovalOnlyAcceptsKnownStatus() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);
        catalog.closeApproval(" ", "DONE");
        catalog.closeApproval("ap-1", "OPEN");
        verifyNoInteractions(todos);

        catalog.closeApproval("ap-1", "DONE");
        verify(todos).closeApproval("ap-1", "DONE");
        catalog.closeApproval("ap-1", "CANCELLED");
        verify(todos).closeApproval("ap-1", "CANCELLED");
    }
}
