package com.zhu.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.admin.api.run.RunDetail;
import com.zhu.admin.api.run.RunRow;
import com.zhu.admin.application.run.MybatisAdminRunQuery;
import com.zhu.readmodel.entity.run.AgentRun;
import com.zhu.readmodel.entity.run.AgentRunStep;
import com.zhu.readmodel.persistence.run.AgentRunMapper;
import com.zhu.readmodel.persistence.run.AgentRunStepMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 管理端运行查询：过滤条件下沉到 Mapper，条数先收口；详情把步骤一起读出来。
 */
class MybatisAdminRunQueryTest {

    private final AgentRunMapper runs = mock(AgentRunMapper.class);

    private final AgentRunStepMapper steps = mock(AgentRunStepMapper.class);

    private final MybatisAdminRunQuery query = new MybatisAdminRunQuery(runs, steps);

    @Test
    void runsPassesFiltersClampsLimitAndMapsRows() {
        AgentRun row = run("run-1", null);
        when(runs.search("u-1", "s-1", "FAILED", 100)).thenReturn(List.of(row));

        List<RunRow> result = query.runs("u-1", "s-1", "FAILED", 500);

        assertThat(result).hasSize(1);
        RunRow mapped = result.get(0);
        assertThat(mapped.runId()).isEqualTo("run-1");
        assertThat(mapped.status()).isEqualTo("FAILED");
        assertThat(mapped.requestId()).isEmpty();
        assertThat(mapped.startedAt()).isEqualTo("2026-11-23T12:00:00");
        assertThat(mapped.durationMs()).isEqualTo(1200L);
    }

    @Test
    void blankFiltersBecomeNullSoTheSqlDoesNotMatchEmptyStrings() {
        when(runs.search(isNull(), isNull(), isNull(), eq(50))).thenReturn(List.of());

        query.runs("  ", "", null, 0);

        verify(runs).search(isNull(), isNull(), isNull(), eq(50));
    }

    @Test
    void runReadsStepsAndReturnsNullWhenMissing() {
        AgentRun row = run("run-2", "req-2");
        when(runs.findByRunId("run-2")).thenReturn(row);
        AgentRunStep step = new AgentRunStep();
        step.setStepNo(1);
        step.setStepType("TOOL");
        step.setToolId("knowledge_search");
        step.setStatus("SUCCEEDED");
        when(steps.listByRunId("run-2")).thenReturn(List.of(step));

        RunDetail detail = query.run("run-2");

        assertThat(detail).isNotNull();
        assertThat(detail.requestId()).isEqualTo("req-2");
        assertThat(detail.steps()).hasSize(1);
        assertThat(detail.steps().get(0).toolId()).isEqualTo("knowledge_search");

        when(runs.findByRunId("missing")).thenReturn(null);
        assertThat(query.run("missing")).isNull();
        assertThat(query.run("  ")).isNull();
    }

    /** 造一行运行，时间固定，方便断言字符串。 */
    private static AgentRun run(String runId, String requestId) {
        AgentRun row = new AgentRun();
        row.setRunId(runId);
        row.setTenantId("demo");
        row.setUserId("u-1");
        row.setSessionId("s-1");
        row.setRequestId(requestId);
        row.setTriggerType("USER");
        row.setStatus("FAILED");
        row.setModel("dashscope:qwen-plus");
        row.setInputSummary("hi");
        row.setOutputSummary("");
        row.setErrorCode("IllegalStateException");
        row.setDurationMs(1200L);
        row.setStartedAt(LocalDateTime.of(2026, 11, 23, 12, 0, 0));
        row.setFinishedAt(LocalDateTime.of(2026, 11, 23, 12, 0, 1));
        return row;
    }
}
