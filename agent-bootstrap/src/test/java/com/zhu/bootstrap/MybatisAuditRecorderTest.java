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
import static org.mockito.Mockito.when;

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
        new MybatisApprovalRecorder(approvals).requested("ap-1", "shell", "user-a", null, "tenant-a");

        ArgumentCaptor<ApprovalRecord> row = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvals).insert(row.capture());
        assertThat(row.getValue().getApprovalId()).isEqualTo("ap-1");
        assertThat(row.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(row.getValue().getUserId()).isEqualTo("user-a");
        assertThat(row.getValue().getSessionId()).isEmpty();
        assertThat(row.getValue().getToolName()).isEqualTo("shell");
        assertThat(row.getValue().getDecision()).isEqualTo("PENDING");
    }

    /** 占到已批准的单后，结果只写成状态 JSON。 */
    @Test
    void claimWritesExecutionStatus() {
        ApprovalRecordMapper approvals = mock(ApprovalRecordMapper.class);
        ApprovalRecord pending = new ApprovalRecord();
        pending.setApprovalId("ap-9");
        when(approvals.findApprovedUnused("", "user-a", "", "shell")).thenReturn(pending);
        when(approvals.claimExecution(org.mockito.ArgumentMatchers.eq("ap-9"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        String claimed = new MybatisApprovalRecorder(approvals).claimApproved(null, "user-a", null, "shell");
        new MybatisApprovalRecorder(approvals).executed(claimed, "SUCCEEDED");

        assertThat(claimed).isEqualTo("ap-9");
        verify(approvals).writeExecutionResult("ap-9", "{\"status\":\"SUCCEEDED\"}");
    }

    /** 同会话同工具的 PENDING 单号交出去，空工具名不查。 */
    @Test
    void findPendingReturnsExistingId() {
        ApprovalRecordMapper approvals = mock(ApprovalRecordMapper.class);
        ApprovalRecord pending = new ApprovalRecord();
        pending.setApprovalId("ap-pending");
        when(approvals.findPendingUnused("tenant-a", "user-a", "session-a", "shell")).thenReturn(pending);

        MybatisApprovalRecorder recorder = new MybatisApprovalRecorder(approvals);

        assertThat(recorder.findPending("tenant-a", "user-a", "session-a", "shell")).isEqualTo("ap-pending");
        assertThat(recorder.findPending("tenant-a", "user-a", "session-a", "")).isNull();
        verify(approvals).findPendingUnused("tenant-a", "user-a", "session-a", "shell");
    }
}
