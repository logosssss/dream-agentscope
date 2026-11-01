package com.zhu.bootstrap;

import com.zhu.access.api.controller.ApprovalController;
import com.zhu.access.api.dto.PendingApproval;
import com.zhu.access.application.AccessAccountGate;
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
 * 待确认从 MySQL 按当前用户读取。没有用户标识时拒绝。
 */
class ApprovalNotifyTest {

    /** 线程身份用完就清，避免串到下一条。 */
    @AfterEach
    void clearIdentity() {
        UserContext.clear();
        TenantContext.clear();
    }

    /** 只把这个租户和用户的单据交给接口。 */
    @Test
    void pendingReadsCurrentUser() {
        AuditQuery audits = mock(AuditQuery.class);
        when(audits.pendingFor("tenant-a", "user-a", 50)).thenReturn(List.of(row("a-1", "shell")));
        ApprovalController controller = new ApprovalController(audits, openAccounts());
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        ApiResult<List<PendingApproval>> result = controller.pending();

        assertThat(result.data()).containsExactly(new PendingApproval("a-1", "shell"));
        verify(audits).pendingFor("tenant-a", "user-a", 50);
    }

    /** 没有用户时不查库。 */
    @Test
    void missingUserIsRejected() {
        ApprovalController controller = new ApprovalController(mock(AuditQuery.class), openAccounts());
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
        ApprovalController controller = new ApprovalController(audits, accounts);
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        assertThatThrownBy(controller::pending)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(audits, never()).pendingFor(any(), any(), anyInt());
    }

    /** 测试不查租户表。 */
    private static AccessAccountGate openAccounts() {
        return new AccessAccountGate(tenantId -> {
        }, (tenantId, userId) -> {
        });
    }

    /** 构造一条待确认。其余字段接口不返回。 */
    private static ApprovalRow row(String approvalId, String toolName) {
        return new ApprovalRow(approvalId, "user-a", "session-a", toolName, "PENDING",
                LocalDateTime.now(), null, null, null, null);
    }
}
