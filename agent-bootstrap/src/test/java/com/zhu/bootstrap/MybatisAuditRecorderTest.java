package com.zhu.bootstrap;

import com.zhu.audit.infrastructure.persistence.AgentEventAuditMapper;
import com.zhu.audit.infrastructure.persistence.ApprovalRecordMapper;
import com.zhu.audit.infrastructure.persistence.MybatisApprovalRecorder;
import com.zhu.audit.infrastructure.persistence.MybatisAuditRecorder;
import com.zhu.audit.infrastructure.persistence.entity.AgentEventAudit;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 简单插入把字段放进实体交给 BaseMapper。不连接真实 MySQL，也不在测试里拼 SQL。
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

    /** 新审批单的决定是 PENDING，空会话写成空串。 */
    @Test
    void approvalInsertsPendingRow() {
        ApprovalRecordMapper approvals = mock(ApprovalRecordMapper.class);
        new MybatisApprovalRecorder(approvals).requested("ap-1", "shell", "user-a", null);

        ArgumentCaptor<ApprovalRecord> row = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvals).insert(row.capture());
        assertThat(row.getValue().getApprovalId()).isEqualTo("ap-1");
        assertThat(row.getValue().getUserId()).isEqualTo("user-a");
        assertThat(row.getValue().getSessionId()).isEmpty();
        assertThat(row.getValue().getToolName()).isEqualTo("shell");
        assertThat(row.getValue().getDecision()).isEqualTo("PENDING");
    }
}
