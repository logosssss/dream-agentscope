package com.zhu.bootstrap;

import com.zhu.access.application.chat.run.MysqlRunQuery;
import com.zhu.access.infrastructure.persistence.run.AgentRunMapper;
import com.zhu.access.infrastructure.persistence.run.AgentRunStepMapper;
import com.zhu.access.infrastructure.persistence.entity.run.AgentRun;
import com.zhu.access.infrastructure.persistence.entity.run.AgentRunStep;
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
 * 运行列表只读已经结束的行。详情才读步骤。不连接 MySQL。
 */
class RunQueryTest {

    /** 只列出本人本租户的行。空白租户按空串。条数 0 按默认 50。 */
    @Test
    void ownedListsRowsForTenant() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        AgentRunStepMapper steps = mock(AgentRunStepMapper.class);
        MysqlRunQuery query = new MysqlRunQuery(runs, steps);
        AgentRun row = run("run-1", "t");
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
        verify(steps, never()).listByRunId(any());
        assertThat(query.owned("u", " ", 200)).isEmpty();
        verify(runs).listOwned("u", "", 100);
    }

    /** 空用户按请求错误，不查库。 */
    @Test
    void ownedRejectsBlankUser() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        AgentRunStepMapper steps = mock(AgentRunStepMapper.class);
        MysqlRunQuery query = new MysqlRunQuery(runs, steps);
        assertThatThrownBy(() -> query.owned(" ", "t", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(runs, never()).listOwned(any(), any(), anyInt());
    }

    /** 先确认归属再读步骤，并带上开始和结束时间。没有步骤时仍返回运行本身。 */
    @Test
    void ownedOneReturnsStepsAfterOwnership() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        AgentRunStepMapper steps = mock(AgentRunStepMapper.class);
        MysqlRunQuery query = new MysqlRunQuery(runs, steps);
        AgentRun row = run("run-1", "t");
        row.setStartedAt(LocalDateTime.of(2026, 11, 2, 1, 2, 3));
        row.setFinishedAt(LocalDateTime.of(2026, 11, 2, 1, 2, 15));
        when(runs.findOwned("u", "t", "run-1")).thenReturn(row);
        AgentRunStep reasoning = new AgentRunStep();
        reasoning.setStepNo(1);
        reasoning.setStepType("REASONING");
        reasoning.setStatus("SUCCEEDED");
        AgentRunStep tool = new AgentRunStep();
        tool.setStepNo(2);
        tool.setStepType("TOOL");
        tool.setToolId("echo_text");
        tool.setStatus("SUCCEEDED");
        when(steps.listByRunId("run-1")).thenReturn(List.of(reasoning, tool));
        assertThat(query.ownedOne("u", "t", "run-1")).satisfies(detail -> {
            assertThat(detail.run().runId()).isEqualTo("run-1");
            assertThat(detail.steps()).hasSize(2);
            assertThat(detail.steps().get(0).stepType()).isEqualTo("REASONING");
            assertThat(detail.steps().get(1).toolId()).isEqualTo("echo_text");
            assertThat(detail.startedAt()).isEqualTo("2026-11-02T01:02:03");
            assertThat(detail.finishedAt()).isEqualTo("2026-11-02T01:02:15");
        });
        when(runs.findOwned("u", "", "run-2")).thenReturn(run("run-2", ""));
        when(steps.listByRunId("run-2")).thenReturn(List.of());
        assertThat(query.ownedOne("u", " ", "run-2").steps()).isEmpty();
        assertThat(query.ownedOne("u", " ", "run-2").startedAt()).isEqualTo("");
        assertThat(query.ownedOne("u", " ", "run-2").finishedAt()).isEqualTo("");
    }

    /** 别人的运行或空标识都不读步骤。 */
    @Test
    void ownedOneRejectsMissingOrBlank() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        AgentRunStepMapper steps = mock(AgentRunStepMapper.class);
        MysqlRunQuery query = new MysqlRunQuery(runs, steps);
        when(runs.findOwned("u", "t", "run-x")).thenReturn(null);
        assertThatThrownBy(() -> query.ownedOne("u", "t", "run-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(runs).findOwned("u", "t", "run-x");
        verify(steps, never()).listByRunId(any());
        assertThatThrownBy(() -> query.ownedOne("u", "t", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(runs, never()).findOwned(any(), any(), eq(" "));
    }

    /** 测试行只填详情会读到的字段。 */
    private static AgentRun run(String runId, String tenantId) {
        AgentRun row = new AgentRun();
        row.setRunId(runId);
        row.setUserId("u");
        row.setTenantId(tenantId);
        row.setSessionId("s");
        row.setTriggerType("USER");
        row.setStatus("SUCCEEDED");
        row.setModel("dashscope:qwen");
        row.setInputSummary("hello");
        row.setOutputSummary("hi");
        row.setDurationMs(12L);
        return row;
    }
}
