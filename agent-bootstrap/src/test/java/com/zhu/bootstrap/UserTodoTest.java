package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhu.audit.application.approval.MysqlUserTodoCatalog;
import com.zhu.audit.infrastructure.persistence.approval.UserTodoMapper;
import com.zhu.audit.infrastructure.persistence.entity.approval.UserTodo;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 确认和任务由用户登记、查看、完成或取消。APPROVAL 类型已硬删。不连接 MySQL。
 */
class UserTodoTest {

    /** 列表只映射还开着的 CONFIRM/TASK。空用户不查库。 */
    @Test
    void listRequiresAUser() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);
        assertThatThrownBy(() -> catalog.listOpen(" ", 50))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoInteractions(todos);

        UserTodo row = new UserTodo();
        row.setId(4L);
        row.setTodoType("CONFIRM");
        row.setRefId("run-1");
        row.setTitle("确认");
        row.setStatus("OPEN");
        when(todos.listOpen("u", 50)).thenReturn(List.of(row));
        assertThat(catalog.listOpen("u", 50)).singleElement().satisfies(saved -> {
            assertThat(saved.refId()).isEqualTo("run-1");
            assertThat(saved.title()).isEqualTo("确认");
            assertThat(saved.status()).isEqualTo("OPEN");
        });
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

    /** 只关这个用户还开着的确认或任务。别人的行不改。 */
    @Test
    void completeUpdatesOwnOpenConfirm() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);

        assertThatThrownBy(() -> catalog.complete("u", 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(todos, never()).closeOpen(any(), any(), any());

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
        verify(todos, never()).closeOpen(any(), any(), any());

        UserTodo open = new UserTodo();
        open.setId(6L);
        open.setUserId("u");
        open.setTodoType("CONFIRM");
        open.setRefId("run-1");
        open.setTitle("确认结果");
        open.setStatus("OPEN");
        when(todos.selectById(6L)).thenReturn(open);
        when(todos.closeOpen(6L, "u", "DONE")).thenReturn(1);
        assertThat(catalog.complete("u", 6L)).satisfies(saved -> {
            assertThat(saved.todoType()).isEqualTo("CONFIRM");
            assertThat(saved.status()).isEqualTo("DONE");
        });

        UserTodo task = new UserTodo();
        task.setId(7L);
        task.setUserId("u");
        task.setTodoType("TASK");
        task.setRefId("task-1");
        task.setStatus("OPEN");
        when(todos.selectById(7L)).thenReturn(task);
        when(todos.closeOpen(7L, "u", "CANCELLED")).thenReturn(1);
        assertThat(catalog.cancel("u", 7L).status()).isEqualTo("CANCELLED");
    }

    /** 已关闭的也返回。创建时间为空时用空串。 */
    @Test
    void ownedOneReturnsClosedWithCreatedAt() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);
        UserTodo done = new UserTodo();
        done.setId(6L);
        done.setTodoType("CONFIRM");
        done.setRefId("run-1");
        done.setTitle("确认结果");
        done.setStatus("DONE");
        done.setCreatedAt(LocalDateTime.of(2026, 11, 2, 1, 2, 3));
        when(todos.findOwned(6L, "u")).thenReturn(done);
        assertThat(catalog.ownedOne("u", 6L)).satisfies(detail -> {
            assertThat(detail.id()).isEqualTo(6L);
            assertThat(detail.status()).isEqualTo("DONE");
            assertThat(detail.createdAt()).isEqualTo("2026-11-02T01:02:03");
        });
        UserTodo noTime = new UserTodo();
        noTime.setId(8L);
        noTime.setTodoType("TASK");
        noTime.setRefId("task-1");
        noTime.setStatus("OPEN");
        when(todos.findOwned(8L, "u")).thenReturn(noTime);
        assertThat(catalog.ownedOne("u", 8L).createdAt()).isEqualTo("");
    }

    /** 别人的主键或空标识都不查详情。 */
    @Test
    void ownedOneRejectsMissingOrBlank() {
        UserTodoMapper todos = mock(UserTodoMapper.class);
        MysqlUserTodoCatalog catalog = new MysqlUserTodoCatalog(todos);
        when(todos.findOwned(9L, "u")).thenReturn(null);
        assertThatThrownBy(() -> catalog.ownedOne("u", 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(todos).findOwned(9L, "u");
        assertThatThrownBy(() -> catalog.ownedOne("u", 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(todos, never()).findOwned(eq(0L), any());
        assertThatThrownBy(() -> catalog.ownedOne(" ", 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(todos, never()).findOwned(anyLong(), eq(" "));
    }
}
