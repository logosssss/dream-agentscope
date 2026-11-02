package com.zhu.bootstrap;

import com.zhu.access.application.MysqlTaskQuery;
import com.zhu.access.infrastructure.persistence.AgentTaskMapper;
import com.zhu.access.infrastructure.persistence.entity.AgentTaskRow;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务列表只读已经落下的状态。不连接 MySQL，也不读任务参数。
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
}
