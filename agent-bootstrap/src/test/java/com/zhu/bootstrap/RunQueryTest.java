package com.zhu.bootstrap;

import com.zhu.access.application.MysqlRunQuery;
import com.zhu.access.infrastructure.persistence.AgentRunMapper;
import com.zhu.access.infrastructure.persistence.entity.AgentRun;
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
 * 运行列表只读已经结束的行。不连接 MySQL，也不读步骤。
 */
class RunQueryTest {

    /** 只列出本人本租户的行。空白租户按空串。条数 0 按默认 50。 */
    @Test
    void ownedListsRowsForTenant() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        MysqlRunQuery query = new MysqlRunQuery(runs);
        AgentRun row = new AgentRun();
        row.setRunId("run-1");
        row.setUserId("u");
        row.setTenantId("t");
        row.setSessionId("s");
        row.setTriggerType("USER");
        row.setStatus("SUCCEEDED");
        row.setModel("dashscope:qwen");
        row.setInputSummary("hello");
        row.setOutputSummary("hi");
        row.setDurationMs(12L);
        when(runs.listOwned("u", "t", 50)).thenReturn(List.of(row));
        assertThat(query.owned("u", "t", 0)).satisfies(list -> {
            assertThat(list).hasSize(1);
            assertThat(list.get(0).runId()).isEqualTo("run-1");
            assertThat(list.get(0).sessionId()).isEqualTo("s");
            assertThat(list.get(0).status()).isEqualTo("SUCCEEDED");
            assertThat(list.get(0).inputSummary()).isEqualTo("hello");
            assertThat(list.get(0).durationMs()).isEqualTo(12L);
        });
        verify(runs).listOwned("u", "t", 50);
        assertThat(query.owned("u", " ", 200)).isEmpty();
        verify(runs).listOwned("u", "", 100);
    }

    /** 空用户按请求错误，不查库。 */
    @Test
    void ownedRejectsBlankUser() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        MysqlRunQuery query = new MysqlRunQuery(runs);
        assertThatThrownBy(() -> query.owned(" ", "t", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(runs, never()).listOwned(any(), any(), anyInt());
    }
}
