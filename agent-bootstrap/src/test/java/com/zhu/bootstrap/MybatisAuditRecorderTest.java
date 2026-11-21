package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.persistence.event.MybatisAuditRecorder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.zhu.readmodel.entity.event.AgentEventAudit;
import com.zhu.readmodel.persistence.event.AgentEventAuditMapper;
import com.zhu.runtime.api.event.AuditRecorder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 简单插入把字段放进实体交给 BaseMapper。不连接真实 MySQL，也不在测试里拼 SQL。
 * 旧审批 Recorder 已硬删。
 */
class MybatisAuditRecorderTest {

    /** 只说动作、不带归属时，会话与运行留空，payload 仍只包一层 detail。 */
    @Test
    void auditInsertsExecutorEvent() {
        AgentEventAuditMapper events = mock(AgentEventAuditMapper.class);
        new MybatisAuditRecorder(events).record("agent.call", "user-a", "session=\"s1\"");

        ArgumentCaptor<AgentEventAudit> row = ArgumentCaptor.forClass(AgentEventAudit.class);
        verify(events).insert(row.capture());
        assertThat(row.getValue().getEventType()).isEqualTo("agent.call");
        assertThat(row.getValue().getUserId()).isEqualTo("user-a");
        assertThat(row.getValue().getSource()).isEqualTo("EXECUTOR");
        assertThat(row.getValue().getSessionId()).isNull();
        assertThat(row.getValue().getRunId()).isNull();
        assertThat(row.getValue().getPayload()).isEqualTo("{\"detail\":\"session=\\\"s1\\\"\"}");
    }

    /**
     * 执行内核写入时带上会话与运行标识：审计行靠 run_id 才能回到具体那一轮。
     * 空白一律落 null，插入策略会跳过它们。
     */
    @Test
    void auditInsertsSessionAndRunWhenGiven() {
        AgentEventAuditMapper events = mock(AgentEventAuditMapper.class);
        AuditRecorder recorder = new MybatisAuditRecorder(events);
        recorder.record("agent.call.finish", "user-a", "s1", "run-1", "session=s1");
        recorder.record("agent.call.error", "user-a", "  ", "", "boom");

        ArgumentCaptor<AgentEventAudit> rows = ArgumentCaptor.forClass(AgentEventAudit.class);
        verify(events, times(2)).insert(rows.capture());
        List<AgentEventAudit> captured = rows.getAllValues();
        assertThat(captured.get(0).getSessionId()).isEqualTo("s1");
        assertThat(captured.get(0).getRunId()).isEqualTo("run-1");
        // 空白会话/运行落 null：插入策略会跳过它们，列保持 NULL。
        assertThat(captured.get(1).getSessionId()).isNull();
        assertThat(captured.get(1).getRunId()).isNull();
    }
}
