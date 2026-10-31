package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlTenantDirectory;
import com.zhu.audit.infrastructure.persistence.TenantMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户登记和对话前检查。不连接 MySQL，也不建用户。
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
}
