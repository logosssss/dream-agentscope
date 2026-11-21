package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.persistence.event.MybatisAuditRecorder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zhu.readmodel.entity.event.AgentEventAudit;
import com.zhu.readmodel.persistence.event.AgentEventAuditMapper;
import com.zhu.runtime.api.event.AuditRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 简单插入把字段放进实体交给 BaseMapper。不连接真实 MySQL，也不在测试里拼 SQL。
 * 旧审批 Recorder 已硬删。
 */
class MybatisAuditRecorderTest {

    /** 说明放进 JSON，来源固定为 EXECUTOR，会话留给空值。 */
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
        assertThat(row.getValue().getPayload()).isEqualTo("{\"detail\":\"session=\\\"s1\\\"\"}");
    }
}
