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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 待确认给发起人开审批待办。确认和任务由用户登记并完成。不连接 MySQL。
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

    /** 只开确认和任务。审批类型拒绝。同一引用再开一次是冲突。 */
    @Test
    void openRegistersConfirmOrTask() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);

        assertThatThrownBy(() -> catalog.open("u", "APPROVAL", "ap-1", "shell"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(todos, never()).insert(any(UserTodo.class));

        assertThat(catalog.open("u", "CONFIRM", "run-1", "确认结果")).satisfies(saved -> {
            assertThat(saved.todoType()).isEqualTo("CONFIRM");
            assertThat(saved.refId()).isEqualTo("run-1");
            assertThat(saved.title()).isEqualTo("确认结果");
            assertThat(saved.status()).isEqualTo("OPEN");
        });
        ArgumentCaptor<UserTodo> saved = ArgumentCaptor.forClass(UserTodo.class);
        verify(todos).insert(saved.capture());
        assertThat(saved.getValue().getTodoType()).isEqualTo("CONFIRM");

        when(todos.insert(any(UserTodo.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.open("u", "TASK", "task-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 只关这个用户还开着的确认或任务。审批和别人的行不改。 */
    @Test
    void completeUpdatesOwnOpenConfirm() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);

        assertThatThrownBy(() -> catalog.complete("u", 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(todos, never()).completeOpen(any(), any());

        when(todos.selectById(4L)).thenReturn(null);
        assertThatThrownBy(() -> catalog.complete("u", 4L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        UserTodo other = new UserTodo();
        other.setId(4L);
        other.setUserId("other");
        other.setTodoType("CONFIRM");
        other.setStatus("OPEN");
        when(todos.selectById(4L)).thenReturn(other);
        assertThatThrownBy(() -> catalog.complete("u", 4L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(todos, never()).completeOpen(any(), any());

        UserTodo approval = new UserTodo();
        approval.setId(5L);
        approval.setUserId("u");
        approval.setTodoType("APPROVAL");
        approval.setStatus("OPEN");
        when(todos.selectById(5L)).thenReturn(approval);
        assertThatThrownBy(() -> catalog.complete("u", 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        UserTodo open = new UserTodo();
        open.setId(6L);
        open.setUserId("u");
        open.setTodoType("CONFIRM");
        open.setRefId("run-1");
        open.setTitle("确认结果");
        open.setStatus("OPEN");
        when(todos.selectById(6L)).thenReturn(open);
        when(todos.completeOpen(6L, "u")).thenReturn(1);
        assertThat(catalog.complete("u", 6L)).satisfies(saved -> {
            assertThat(saved.todoType()).isEqualTo("CONFIRM");
            assertThat(saved.status()).isEqualTo("DONE");
        });
    }
}
