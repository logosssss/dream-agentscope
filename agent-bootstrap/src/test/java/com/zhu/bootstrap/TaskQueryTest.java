package com.zhu.bootstrap;

import com.zhu.access.application.account.MysqlTaskQuery;
import com.zhu.access.infrastructure.persistence.task.AgentTaskMapper;
import com.zhu.access.infrastructure.persistence.entity.task.AgentTaskRow;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务列表只读已经落下的状态。详情才带开始结束时间。不连接 MySQL，也不读任务参数。
 */
class TaskQueryTest {

    /** 只列出本人本租户的行。空白租户按空串。条数 0 按默认 50。 */
    @Test
    void ownedListsRowsForTenant() {
        AgentTaskMapper tasks = mock(AgentTaskMapper.class);
        MysqlTaskQuery query = new MysqlTaskQuery(tasks);
        AgentTaskRow row = new AgentTaskRow();
        row.setTaskId("task-1");
        row.setUserId("u");
        row.setTenantId("t");
        row.setSessionId("s");
        row.setTaskType("doc-ingest");
        row.setStatus("SUCCEEDED");
        when(tasks.listOwned("u", "t", 50)).thenReturn(List.of(row));
        assertThat(query.owned("u", "t", 0)).satisfies(list -> {
            assertThat(list).hasSize(1);
            assertThat(list.get(0).taskId()).isEqualTo("task-1");
            assertThat(list.get(0).taskType()).isEqualTo("doc-ingest");
            assertThat(list.get(0).status()).isEqualTo("SUCCEEDED");
        });
        verify(tasks).listOwned("u", "t", 50);
        assertThat(query.owned("u", " ", 200)).isEmpty();
        verify(tasks).listOwned("u", "", 100);
    }

    /** 空用户按请求错误，不查库。 */
    @Test
    void ownedRejectsBlankUser() {
        AgentTaskMapper tasks = mock(AgentTaskMapper.class);
        MysqlTaskQuery query = new MysqlTaskQuery(tasks);
        assertThatThrownBy(() -> query.owned(" ", "t", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(tasks, never()).listOwned(any(), any(), anyInt());
    }

    /** 先确认归属再带开始结束时间。进行中的结束时间为空。 */
    @Test
    void ownedOneReturnsTimesAfterOwnership() {
        AgentTaskMapper tasks = mock(AgentTaskMapper.class);
        MysqlTaskQuery query = new MysqlTaskQuery(tasks);
        AgentTaskRow row = new AgentTaskRow();
        row.setTaskId("task-1");
        row.setUserId("u");
        row.setTenantId("t");
        row.setSessionId("s");
        row.setTaskType("doc-ingest");
        row.setStatus("RUNNING");
        row.setStartedAt(LocalDateTime.of(2026, 11, 2, 1, 2, 3));
        when(tasks.findOwned("u", "t", "task-1")).thenReturn(row);
        assertThat(query.ownedOne("u", "t", "task-1")).satisfies(detail -> {
            assertThat(detail.taskId()).isEqualTo("task-1");
            assertThat(detail.status()).isEqualTo("RUNNING");
            assertThat(detail.startedAt()).isEqualTo("2026-11-02T01:02:03");
            assertThat(detail.finishedAt()).isEqualTo("");
        });
        AgentTaskRow finished = new AgentTaskRow();
        finished.setTaskId("task-2");
        finished.setUserId("u");
        finished.setTenantId("");
        finished.setStatus("SUCCEEDED");
        finished.setFinishedAt(LocalDateTime.of(2026, 11, 2, 4, 5, 6));
        when(tasks.findOwned("u", "", "task-2")).thenReturn(finished);
        assertThat(query.ownedOne("u", " ", "task-2").finishedAt()).isEqualTo("2026-11-02T04:05:06");
    }

    /** 别人的任务或空标识都不查详情。 */
    @Test
    void ownedOneRejectsMissingOrBlank() {
        AgentTaskMapper tasks = mock(AgentTaskMapper.class);
        MysqlTaskQuery query = new MysqlTaskQuery(tasks);
        when(tasks.findOwned("u", "t", "task-x")).thenReturn(null);
        assertThatThrownBy(() -> query.ownedOne("u", "t", "task-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(tasks).findOwned("u", "t", "task-x");
        assertThatThrownBy(() -> query.ownedOne("u", "t", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(tasks, never()).findOwned(any(), any(), eq(" "));
    }
}
