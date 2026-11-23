package com.zhu.bootstrap;

import com.zhu.access.application.tenant.MysqlTenantUserCatalog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.access.application.quota.MysqlTenantQuota;
import com.zhu.access.application.tenant.MysqlTenantDirectory;
import com.zhu.access.api.tenant.TenantUserCatalog;
import com.zhu.readmodel.entity.tenant.TenantRecord;
import com.zhu.readmodel.entity.tenant.TenantUser;
import com.zhu.readmodel.persistence.quota.TenantQuotaMapper;
import com.zhu.readmodel.persistence.tenant.TenantMapper;
import com.zhu.readmodel.persistence.tenant.TenantUserMapper;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 审计读穿：第二次 requireActive / 额度 Lookup 不打 Mapper；写后删键；Redis 抛 UNAVAILABLE 仍查库。
 */
class AccessReadThroughCacheTest {

    @Test
    void tenantActiveHitsCacheOnSecondCall() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantRecord active = new TenantRecord();
        active.setStatus("ACTIVE");
        when(tenants.findByTenantId("t1")).thenReturn(active);
        FakeCacheFacade cache = new FakeCacheFacade();
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants, cache);

        directory.requireActive("t1");
        directory.requireActive("t1");
        verify(tenants, times(1)).findByTenantId("t1");
    }

    @Test
    void suspendInvalidatesTenantActiveCache() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantRecord active = new TenantRecord();
        active.setTenantId("t1");
        active.setName("n");
        active.setPlan("standard");
        active.setStatus("ACTIVE");
        when(tenants.findByTenantId("t1")).thenReturn(active);
        when(tenants.suspendActive("t1")).thenReturn(1);
        FakeCacheFacade cache = new FakeCacheFacade();
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants, cache);

        directory.requireActive("t1");
        directory.suspend("t1");

        TenantRecord suspended = new TenantRecord();
        suspended.setStatus("SUSPENDED");
        when(tenants.findByTenantId("t1")).thenReturn(suspended);
        assertThatThrownBy(() -> directory.requireActive("t1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(tenants, times(3)).findByTenantId("t1");
    }

    @Test
    void userActiveHitsCacheOnSecondCall() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantUserMapper users = mock(TenantUserMapper.class);
        TenantUser row = new TenantUser();
        row.setTenantId("t1");
        row.setUserId("u1");
        row.setStatus("ACTIVE");
        when(users.findByTenantAndUser("t1", "u1")).thenReturn(row);
        FakeCacheFacade cache = new FakeCacheFacade();
        MysqlTenantUserCatalog catalog = new MysqlTenantUserCatalog(tenants, users, cache);

        catalog.requireActive("t1", "u1");
        catalog.requireActive("t1", "u1");
        verify(users, times(1)).findByTenantAndUser("t1", "u1");
    }

    @Test
    void quotaLookupCachesLimitAndNone() {
        TenantQuotaMapper quotas = mock(TenantQuotaMapper.class);
        when(quotas.findDailyRequestLimit("t1")).thenReturn(10L);
        when(quotas.findDailyTokenLimit("t1")).thenReturn(null);
        FakeCacheFacade cache = new FakeCacheFacade();
        MysqlTenantQuota directory = new MysqlTenantQuota(quotas, cache);

        assertThat(directory.dailyRequestLimit("t1")).isEqualTo(10L);
        assertThat(directory.dailyRequestLimit("t1")).isEqualTo(10L);
        verify(quotas, times(1)).findDailyRequestLimit("t1");

        assertThat(directory.dailyTokenLimit("t1")).isNull();
        assertThat(directory.dailyTokenLimit("t1")).isNull();
        verify(quotas, times(1)).findDailyTokenLimit("t1");
    }

    @Test
    void redisUnavailableFallsThroughToMysql() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantRecord active = new TenantRecord();
        active.setStatus("ACTIVE");
        when(tenants.findByTenantId("t1")).thenReturn(active);
        MysqlTenantDirectory directory = new MysqlTenantDirectory(tenants, new ThrowingCacheFacade());

        assertThatCode(() -> directory.requireActive("t1")).doesNotThrowAnyException();
        verify(tenants, times(1)).findByTenantId("t1");
    }
}
