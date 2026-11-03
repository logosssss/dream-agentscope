package com.zhu.bootstrap;

import com.zhu.access.api.controller.ApprovalController;
import com.zhu.access.api.dto.ApprovalDecisionRequest;
import com.zhu.access.api.dto.ApprovalDecisionView;
import com.zhu.access.api.dto.PendingApproval;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.api.ApprovalDecider;
import com.zhu.audit.api.ApprovalRow;
import com.zhu.audit.api.AuditQuery;
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
