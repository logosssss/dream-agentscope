package com.zhu.bootstrap;

import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.api.UserTodoCatalog;
import com.zhu.audit.application.MybatisApprovalDecider;
import com.zhu.audit.infrastructure.persistence.ApprovalRecordMapper;
import com.zhu.audit.infrastructure.persistence.entity.ApprovalRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 决定更新只打在仍为 PENDING 的行上。通过后关待办。不连接真实 MySQL。
 */
class MybatisApprovalDeciderTest {

    /** 通过后带回审批人和决定时间，并关掉待办。不改执行结果。 */
    @Test
    void approvesPendingRow() {
        ApprovalRecordMapper approvals = mock(ApprovalRecordMapper.class);
        UserTodoCatalog todos = mock(UserTodoCatalog.class);
        ApprovalRecord pending = pending("ap-1");
        when(approvals.findByApprovalId("ap-1")).thenReturn(pending);
        when(approvals.decidePending(eq("ap-1"), eq("APPROVED"), eq("ops"), eq("CONSOLE"), eq("ok"), any()))
                .thenReturn(1);

        ApprovalRow row = new MybatisApprovalDecider(approvals, todos)
                .decide("ap-1", ApprovalDecision.APPROVED, "ops", "CONSOLE", "ok");

        ArgumentCaptor<LocalDateTime> decidedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(approvals).decidePending(eq("ap-1"), eq("APPROVED"), eq("ops"), eq("CONSOLE"), eq("ok"), decidedAt.capture());
        assertThat(row.decision()).isEqualTo("APPROVED");
        assertThat(row.approver()).isEqualTo("ops");
        assertThat(row.channel()).isEqualTo("CONSOLE");
        assertThat(row.reason()).isEqualTo("ok");
        assertThat(row.decidedAt()).isEqualTo(decidedAt.getValue());
        assertThat(row.toolName()).isEqualTo("shell");
        verify(todos).closeApproval("ap-1", "DONE");
    }

    /** 单号不存在时不发更新。 */
    @Test
    void missingRowIsNotFound() {
        ApprovalRecordMapper approvals = mock(ApprovalRecordMapper.class);
        when(approvals.findByApprovalId("missing")).thenReturn(null);

        UserTodoCatalog todos = mock(UserTodoCatalog.class);
        assertThatThrownBy(() -> new MybatisApprovalDecider(approvals, todos)
                .decide("missing", ApprovalDecision.REJECTED, "ops", "CONSOLE", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(approvals, never()).decidePending(any(), any(), any(), any(), any(), any());
        verify(todos, never()).closeApproval(any(), any());
    }

    /** 已经决定过的单据保持原决定。 */
    @Test
    void decidedRowConflicts() {
        ApprovalRecordMapper approvals = mock(ApprovalRecordMapper.class);
        ApprovalRecord decided = pending("ap-1");
        decided.setDecision("REJECTED");
        when(approvals.findByApprovalId("ap-1")).thenReturn(decided);

        UserTodoCatalog todos = mock(UserTodoCatalog.class);
        assertThatThrownBy(() -> new MybatisApprovalDecider(approvals, todos)
                .decide("ap-1", ApprovalDecision.APPROVED, "ops", "CONSOLE", "again"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(approvals, never()).decidePending(any(), any(), any(), any(), any(), any());
        verify(todos, never()).closeApproval(any(), any());
    }

    /** 读到 PENDING 但更新没抢到行，按冲突处理。 */
    @Test
    void lostUpdateConflicts() {
        ApprovalRecordMapper approvals = mock(ApprovalRecordMapper.class);
        when(approvals.findByApprovalId("ap-1")).thenReturn(pending("ap-1"));
        when(approvals.decidePending(any(), any(), any(), any(), any(), any())).thenReturn(0);

        UserTodoCatalog todos = mock(UserTodoCatalog.class);
        assertThatThrownBy(() -> new MybatisApprovalDecider(approvals, todos)
                .decide("ap-1", ApprovalDecision.APPROVED, "ops", "CONSOLE", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(todos, never()).closeApproval(any(), any());
    }

    /** 待办关闭失败不改已经写下的决定。 */
    @Test
    void todoCloseFailureKeepsDecision() {
        ApprovalRecordMapper approvals = mock(ApprovalRecordMapper.class);
        UserTodoCatalog todos = mock(UserTodoCatalog.class);
        when(approvals.findByApprovalId("ap-1")).thenReturn(pending("ap-1"));
        when(approvals.decidePending(any(), any(), any(), any(), any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("db")).when(todos).closeApproval("ap-1", "CANCELLED");

        ApprovalRow row = new MybatisApprovalDecider(approvals, todos)
                .decide("ap-1", ApprovalDecision.REJECTED, "ops", "CONSOLE", "no");

        assertThat(row.decision()).isEqualTo("REJECTED");
        verify(todos).closeApproval("ap-1", "CANCELLED");
    }

    private static ApprovalRecord pending(String approvalId) {
        ApprovalRecord row = new ApprovalRecord();
        row.setApprovalId(approvalId);
        row.setUserId("user-a");
        row.setSessionId("s1");
        row.setToolName("shell");
        row.setDecision("PENDING");
        row.setRequestedAt(LocalDateTime.parse("2026-10-30T03:00:00"));
        return row;
    }
}
