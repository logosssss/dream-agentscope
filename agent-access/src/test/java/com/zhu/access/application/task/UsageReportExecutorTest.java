package com.zhu.access.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.runtime.task.AgentTask;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 报表只读三张表，数字进审计、正文不进审计。
 */
class UsageReportExecutorTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private final List<String> audits = new ArrayList<>();

    private final AuditRecorder audit = (action, actor, detail) -> audits.add(action + "|" + actor + "|" + detail);

    private AgentTask task(Map<String, Object> payload) {
        return new AgentTask("t-1", UsageReportExecutor.TYPE, "demo", "u-1", "companion-v1", null, payload, Instant.now());
    }

    @Test
    void writesCountsForRequestedDays() {
        when(jdbc.queryForObject(anyString(), eq(Number.class), eq("demo"), eq("u-1"), any()))
                .thenReturn(1200L, 3L, 42L);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq("demo"), eq("u-1"), any()))
                .thenReturn(new BigDecimal("0.123456"));

        new UsageReportExecutor(jdbc, audit).execute(task(Map.of("days", 30)));

        assertThat(audits).containsExactly("task.report|u-1|days=30,tokens=1200,cost=0.123456,runs=3,messages=42");
    }

    @Test
    void defaultsToSevenDaysAndZeroCost() {
        when(jdbc.queryForObject(anyString(), eq(Number.class), eq("demo"), eq("u-1"), any())).thenReturn(null);
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq("demo"), eq("u-1"), any())).thenReturn(null);

        new UsageReportExecutor(jdbc, audit).execute(task(Map.of()));

        assertThat(audits).containsExactly("task.report|u-1|days=7,tokens=0,cost=0.000000,runs=0,messages=0");
    }
}
