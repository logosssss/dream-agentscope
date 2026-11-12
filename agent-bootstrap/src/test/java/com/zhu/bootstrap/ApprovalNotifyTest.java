package com.zhu.bootstrap;

import com.zhu.access.api.controller.chat.ApprovalController;
import com.zhu.access.api.dto.chat.ApprovalDecisionRequest;
import com.zhu.access.api.dto.chat.ApprovalDecisionView;
import com.zhu.access.api.dto.chat.ApprovalDetail;
import com.zhu.access.api.dto.chat.PendingApproval;
import com.zhu.access.application.auth.gate.AccessAccountGate;
import com.zhu.audit.api.approval.ApprovalDecision;
import com.zhu.audit.api.approval.ApprovalDecider;
import com.zhu.audit.api.approval.ApprovalRow;
import com.zhu.audit.api.event.AuditQuery;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
 * 待确认从 MySQL 按当前用户读取；本人决定走 WEB 通道，不执行工具。
 */
class ApprovalNotifyTest {

    /** 线程身份用完就清，避免串到下一条。 */
    @AfterEach
    void clearIdentity() {
        UserContext.clear();
        TenantContext.clear();
    }

    /** 只把这个租户和用户的单据交给接口，并带上会话。 */
    @Test
    void pendingReadsCurrentUser() {
        AuditQuery audits = mock(AuditQuery.class);
        when(audits.pendingFor("tenant-a", "user-a", 50)).thenReturn(List.of(row("a-1", "shell", "session-a")));
        ApprovalController controller = new ApprovalController(audits, mock(ApprovalDecider.class), openAccounts());
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        ApiResult<List<PendingApproval>> result = controller.pending();

        assertThat(result.data()).containsExactly(new PendingApproval("a-1", "shell", "session-a"));
        verify(audits).pendingFor("tenant-a", "user-a", 50);
    }

    /** 没有用户时不查库。 */
    @Test
    void missingUserIsRejected() {
        ApprovalController controller = new ApprovalController(
                mock(AuditQuery.class), mock(ApprovalDecider.class), openAccounts());
        assertThatThrownBy(controller::pending).isInstanceOf(BusinessException.class);
    }

    /** 账号不可用时不查库。 */
    @Test
    void inactiveAccountIsRejected() {
        AuditQuery audits = mock(AuditQuery.class);
        AccessAccountGate accounts = new AccessAccountGate(
                tenantId -> {
                },
                (tenantId, userId) -> {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "tenant user not found");
                });
        ApprovalController controller = new ApprovalController(audits, mock(ApprovalDecider.class), accounts);
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        assertThatThrownBy(controller::pending)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(audits, never()).pendingFor(any(), any(), anyInt());
    }

    /** 本人本租户才能决定；审批人取当前用户，通道固定 WEB。 */
    @Test
    void decideWritesCurrentUserOnWebChannel() {
        AuditQuery audits = mock(AuditQuery.class);
        ApprovalDecider decisions = mock(ApprovalDecider.class);
        when(audits.ownedOne("tenant-a", "user-a", "a-1")).thenReturn(row("a-1", "shell", "session-a"));
        when(decisions.decide("a-1", ApprovalDecision.APPROVED, "user-a", "WEB", "ok"))
                .thenReturn(decided("a-1", "shell", "session-a", "APPROVED", "user-a"));
        ApprovalController controller = new ApprovalController(audits, decisions, openAccounts());
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        ApiResult<ApprovalDecisionView> result =
                controller.decide("a-1", new ApprovalDecisionRequest("APPROVED", "ok"));

        assertThat(result.data()).isEqualTo(new ApprovalDecisionView("a-1", "shell", "session-a", "APPROVED"));
        verify(decisions).decide("a-1", ApprovalDecision.APPROVED, "user-a", "WEB", "ok");
    }

    /** 别人的单号按找不到，不调用决定口。 */
    @Test
    void decideRejectsForeignApproval() {
        AuditQuery audits = mock(AuditQuery.class);
        ApprovalDecider decisions = mock(ApprovalDecider.class);
        when(audits.ownedOne("tenant-a", "user-a", "a-2")).thenReturn(null);
        ApprovalController controller = new ApprovalController(audits, decisions, openAccounts());
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        assertThatThrownBy(() -> controller.decide("a-2", new ApprovalDecisionRequest("APPROVED", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(decisions, never()).decide(any(), any(), any(), any(), any());
    }

    /** 已决定的也返回申请和决定时间。不调用决定口。 */
    @Test
    void getReturnsDecidedTimesWithoutExecuting() {
        AuditQuery audits = mock(AuditQuery.class);
        ApprovalDecider decisions = mock(ApprovalDecider.class);
        LocalDateTime requested = LocalDateTime.of(2026, 11, 2, 1, 2, 3);
        LocalDateTime decidedAt = LocalDateTime.of(2026, 11, 2, 4, 5, 6);
        when(audits.ownedOne("tenant-a", "user-a", "a-1")).thenReturn(new ApprovalRow(
                "a-1", "user-a", "session-a", "shell", "APPROVED",
                requested, "user-a", "WEB", "ok", decidedAt));
        ApprovalController controller = new ApprovalController(audits, decisions, openAccounts());
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        ApiResult<ApprovalDetail> result = controller.get("a-1");

        assertThat(result.data()).isEqualTo(new ApprovalDetail(
                "a-1", "shell", "session-a", "APPROVED", "2026-11-02T01:02:03", "2026-11-02T04:05:06", "ok"));
        verify(decisions, never()).decide(any(), any(), any(), any(), any());
    }

    /** 待处理的决定时间为空串。别人的或空单号不改库。 */
    @Test
    void getRejectsMissingOrBlank() {
        AuditQuery audits = mock(AuditQuery.class);
        when(audits.ownedOne("tenant-a", "user-a", "a-2")).thenReturn(null);
        when(audits.ownedOne("tenant-a", "user-a", "a-3")).thenReturn(new ApprovalRow(
                "a-3", "user-a", "session-a", "shell", "PENDING",
                null, null, null, null, null));
        ApprovalController controller = new ApprovalController(audits, mock(ApprovalDecider.class), openAccounts());
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        assertThat(controller.get("a-3").data().decidedAt()).isEqualTo("");
        assertThatThrownBy(() -> controller.get("a-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> controller.get(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(audits, never()).ownedOne("tenant-a", "user-a", " ");
    }

    /** 测试不查租户表。 */
    private static AccessAccountGate openAccounts() {
        return new AccessAccountGate(tenantId -> {
        }, (tenantId, userId) -> {
        });
    }

    /** 构造一条待确认。其余字段接口不返回。 */
    private static ApprovalRow row(String approvalId, String toolName, String sessionId) {
        return new ApprovalRow(approvalId, "user-a", sessionId, toolName, "PENDING",
                LocalDateTime.now(), null, null, null, null);
    }

    /** 构造决定之后的单据。 */
    private static ApprovalRow decided(
            String approvalId, String toolName, String sessionId, String decision, String approver) {
        return new ApprovalRow(
                approvalId, "user-a", sessionId, toolName, decision,
                LocalDateTime.now(), approver, "WEB", null, LocalDateTime.now());
    }
}
