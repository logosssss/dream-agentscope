package com.zhu.admin;

import com.zhu.admin.application.AdminOperationAuditHook;
import com.zhu.admin.application.AdminOperationAuditTarget;
import com.zhu.audit.api.OperationAuditCatalog;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理写入成功后按路径补记后台操作。不连接 MySQL，也不改业务表。
 */
class AdminOperationAuditHookTest {

    /** 查询、手工登记和失败响应都不写。没有操作人时记 admin。 */
    @Test
    void recordsRecognizedSuccessfulWrites() {
        OperationAuditCatalog catalog = mock(OperationAuditCatalog.class);
        AdminOperationAuditHook hook = new AdminOperationAuditHook(provider(catalog));

        assertThat(AdminOperationAuditTarget.parse("GET", "/api/admin/tenants")).isNull();
        assertThat(AdminOperationAuditTarget.parse("POST", "/api/admin/operation-audits")).isNull();
        assertThat(AdminOperationAuditTarget.parse("POST", "/api/admin/tenants/t/suspend"))
                .isEqualTo(new AdminOperationAuditTarget("t", "SUSPEND", "tenant", "t"));
        assertThat(AdminOperationAuditTarget.parse("POST", "/api/admin/tenant-users/t/u/disable"))
                .isEqualTo(new AdminOperationAuditTarget("t", "DISABLE", "tenant_user", "u"));
        assertThat(AdminOperationAuditTarget.parse("POST", "/api/admin/eval-runs/run-1/score"))
                .isEqualTo(new AdminOperationAuditTarget("", "SCORE", "eval_run", "run-1"));
        assertThat(AdminOperationAuditTarget.parse("POST", "/api/admin/billing/payments"))
                .isEqualTo(new AdminOperationAuditTarget("", "PAY", "billing", null));

        hook.afterSuccess("POST", "/api/admin/tenants", null);
        verify(catalog).record("admin", "", "CREATE", "tenant", null);

        hook.afterSuccess("GET", "/api/admin/tenants", "op-a");
        hook.afterSuccess("POST", "/api/admin/operation-audits", "op-a");
        verify(catalog, never()).record(eq("op-a"), any(), any(), any(), any());
    }

    /** 目录缺失或写入失败都不抛给调用方。 */
    @Test
    void writeFailureDoesNotSurface() {
        AdminOperationAuditHook missing = new AdminOperationAuditHook(provider(null));
        assertThatCode(() -> missing.afterSuccess("POST", "/api/admin/tenants", "op-a"))
                .doesNotThrowAnyException();

        OperationAuditCatalog catalog = mock(OperationAuditCatalog.class);
        when(catalog.record(any(), any(), any(), any(), isNull()))
                .thenThrow(new BusinessException(ErrorCode.UNAVAILABLE, "down"));
        AdminOperationAuditHook hook = new AdminOperationAuditHook(provider(catalog));
        assertThatCode(() -> hook.afterSuccess("POST", "/api/admin/tenants", "op-a"))
                .doesNotThrowAnyException();
    }

    /** 测试里直接给出目录，不走 Spring 查找。 */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<OperationAuditCatalog> provider(OperationAuditCatalog catalog) {
        ObjectProvider<OperationAuditCatalog> catalogs = mock(ObjectProvider.class);
        when(catalogs.getIfAvailable()).thenReturn(catalog);
        return catalogs;
    }
}
