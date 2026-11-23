package com.zhu.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.admin.api.event.AuditEventRow;
import com.zhu.admin.application.event.MybatisAuditQuery;
import com.zhu.readmodel.entity.event.AgentEventAudit;
import com.zhu.readmodel.persistence.event.AgentEventAuditMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 审计查询：过滤条件下沉到 Mapper，条数先收口，行转成对外的 record。
 */
class MybatisAuditQueryTest {

    private final AgentEventAuditMapper mapper = mock(AgentEventAuditMapper.class);

    private final MybatisAuditQuery query = new MybatisAuditQuery(mapper);

    @Test
    void eventsPassesFiltersAndMapsRows() {
        AgentEventAudit row = new AgentEventAudit();
        row.setEventId("e-1");
        row.setEventType("agent.call.error");
        row.setUserId("u-1");
        row.setSessionId("s-1");
        row.setRunId("run-1");
        row.setSource("EXECUTOR");
        row.setPayload("{\"detail\":\"boom\"}");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 23, 12, 0, 0));
        when(mapper.search("u-1", "s-1", "run-1", 100)).thenReturn(List.of(row));

        List<AuditEventRow> rows = query.events("u-1", "s-1", "run-1", 500);

        assertThat(rows).hasSize(1);
        AuditEventRow mapped = rows.get(0);
        assertThat(mapped.eventId()).isEqualTo("e-1");
        assertThat(mapped.eventType()).isEqualTo("agent.call.error");
        assertThat(mapped.runId()).isEqualTo("run-1");
        assertThat(mapped.sessionId()).isEqualTo("s-1");
        assertThat(mapped.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 23, 12, 0, 0));
    }

    @Test
    void blankFiltersBecomeNullSoTheSqlDoesNotMatchEmptyStrings() {
        when(mapper.search(isNull(), isNull(), isNull(), eq(50))).thenReturn(List.of());

        query.events("  ", "", null, 0);

        verify(mapper).search(isNull(), isNull(), isNull(), eq(50));
    }

    @Test
    void recentEventsIsEventsWithoutFilters() {
        when(mapper.search(isNull(), isNull(), isNull(), eq(50))).thenReturn(List.of());

        assertThat(query.recentEvents(50)).isEmpty();

        verify(mapper).search(isNull(), isNull(), isNull(), eq(50));
    }
}
