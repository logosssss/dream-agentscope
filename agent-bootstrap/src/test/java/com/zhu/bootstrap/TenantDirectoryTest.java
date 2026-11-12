package com.zhu.bootstrap;

import com.zhu.audit.application.tenant.MysqlTenantDirectory;
import com.zhu.audit.infrastructure.persistence.tenant.TenantMapper;
import com.zhu.audit.infrastructure.persistence.entity.tenant.TenantRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户登记、列表、停用、关闭、恢复和接入前检查。不连接 MySQL，也不建用户。
 */
class TenantDirectoryTest {

    /** 新租户是可用的 standard。重复标识按冲突处理。 */
    @Test
    void registerInsertsActiveTenant() {
        TenantMapper tenants = mock(TenantMapper.class);
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants);

        assertThat(directory.register("t", "示例").status()).isEqualTo("ACTIVE");
        verify(tenants).insert(any(TenantRecord.class));

        when(tenants.insert(any(TenantRecord.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> directory.register("t", "示例"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 只有 ACTIVE 能过。没有或不在用时拒绝。 */
    @Test
    void onlyActiveTenantCanCall() {
        TenantMapper tenants = mock(TenantMapper.class);
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants);
        TenantRecord active = new TenantRecord();
        active.setStatus("ACTIVE");
        when(tenants.findByTenantId("t")).thenReturn(active);
        assertThatCode(() -> directory.requireActive("t")).doesNotThrowAnyException();

        TenantRecord suspended = new TenantRecord();
        suspended.setStatus("SUSPENDED");
        when(tenants.findByTenantId("t")).thenReturn(suspended);
        assertThatThrownBy(() -> directory.requireActive("t"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        when(tenants.findByTenantId("missing")).thenReturn(null);
        assertThatThrownBy(() -> directory.requireActive("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 只停还在用的租户。没有或已经停用按找不到或冲突。 */
    @Test
    void suspendUpdatesActiveOnly() {
        TenantMapper tenants = mock(TenantMapper.class);
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants);

        assertThatThrownBy(() -> directory.suspend("t"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(tenants, never()).suspendActive("t");

        TenantRecord active = new TenantRecord();
        active.setTenantId("t");
        active.setName("示例");
        active.setPlan("standard");
        active.setStatus("ACTIVE");
        when(tenants.findByTenantId("t")).thenReturn(active);
        when(tenants.suspendActive("t")).thenReturn(1);
        assertThat(directory.suspend("t")).satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.name()).isEqualTo("示例");
            assertThat(saved.status()).isEqualTo("SUSPENDED");
        });

        TenantRecord suspended = new TenantRecord();
        suspended.setStatus("SUSPENDED");
        when(tenants.findByTenantId("t")).thenReturn(suspended);
        assertThatThrownBy(() -> directory.suspend("t"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(tenants).suspendActive("t");
    }

    /** 可用和停用都能关。没有或已经关闭按找不到或冲突。 */
    @Test
    void closeUpdatesOpenOnly() {
        TenantMapper tenants = mock(TenantMapper.class);
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants);

        assertThatThrownBy(() -> directory.close("t"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(tenants, never()).closeNotClosed("t");

        TenantRecord active = new TenantRecord();
        active.setTenantId("t");
        active.setName("示例");
        active.setPlan("standard");
        active.setStatus("ACTIVE");
        when(tenants.findByTenantId("t")).thenReturn(active);
        when(tenants.closeNotClosed("t")).thenReturn(1);
        assertThat(directory.close("t")).satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.name()).isEqualTo("示例");
            assertThat(saved.status()).isEqualTo("CLOSED");
        });

        TenantRecord suspended = new TenantRecord();
        suspended.setTenantId("t");
        suspended.setName("示例");
        suspended.setPlan("standard");
        suspended.setStatus("SUSPENDED");
        when(tenants.findByTenantId("t")).thenReturn(suspended);
        when(tenants.closeNotClosed("t")).thenReturn(1);
        assertThat(directory.close("t").status()).isEqualTo("CLOSED");

        TenantRecord closed = new TenantRecord();
        closed.setStatus("CLOSED");
        when(tenants.findByTenantId("t")).thenReturn(closed);
        assertThatThrownBy(() -> directory.close("t"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(tenants, times(2)).closeNotClosed("t");
    }

    /** 停用和关闭都能恢复。没有或已经可用按找不到或冲突。 */
    @Test
    void restoreUpdatesInactiveOnly() {
        TenantMapper tenants = mock(TenantMapper.class);
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants);

        assertThatThrownBy(() -> directory.restore("t"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(tenants, never()).restoreInactive("t");

        TenantRecord suspended = new TenantRecord();
        suspended.setTenantId("t");
        suspended.setName("示例");
        suspended.setPlan("standard");
        suspended.setStatus("SUSPENDED");
        when(tenants.findByTenantId("t")).thenReturn(suspended);
        when(tenants.restoreInactive("t")).thenReturn(1);
        assertThat(directory.restore("t")).satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.name()).isEqualTo("示例");
            assertThat(saved.status()).isEqualTo("ACTIVE");
        });

        TenantRecord closed = new TenantRecord();
        closed.setTenantId("t");
        closed.setName("示例");
        closed.setPlan("standard");
        closed.setStatus("CLOSED");
        when(tenants.findByTenantId("t")).thenReturn(closed);
        when(tenants.restoreInactive("t")).thenReturn(1);
        assertThat(directory.restore("t").status()).isEqualTo("ACTIVE");

        TenantRecord active = new TenantRecord();
        active.setStatus("ACTIVE");
        when(tenants.findByTenantId("t")).thenReturn(active);
        assertThatThrownBy(() -> directory.restore("t"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(tenants, times(2)).restoreInactive("t");
    }

    /** 列表含停用和关闭。空结果原样返回。 */
    @Test
    void recentListsAllStatuses() {
        TenantMapper tenants = mock(TenantMapper.class);
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants);
        when(tenants.recent(50)).thenReturn(List.of());
        assertThat(directory.recent(50)).isEmpty();

        TenantRecord closed = new TenantRecord();
        closed.setTenantId("t");
        closed.setName("示例");
        closed.setPlan("standard");
        closed.setStatus("CLOSED");
        when(tenants.recent(50)).thenReturn(List.of(closed));
        assertThat(directory.recent(0)).singleElement().satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.status()).isEqualTo("CLOSED");
        });
    }
}
