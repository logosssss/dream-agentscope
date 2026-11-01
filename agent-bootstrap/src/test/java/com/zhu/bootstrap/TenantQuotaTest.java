package com.zhu.bootstrap;

import com.zhu.access.application.QuotaGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.audit.application.MysqlTenantQuota;
import com.zhu.audit.infrastructure.persistence.TenantQuotaMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantQuotaRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户每日请求上限。不连接 MySQL 或 Redis。进程配置为 0 时，有行才限额。
 */
class TenantQuotaTest {

    /** 有行时第二次超过这一行。没有行时不拦截。 */
    @Test
    void dailyRequestsFollowTheTenantRow() {
        AccessLimitProperties limits = new AccessLimitProperties();
        QuotaGuard limited = new QuotaGuard(new FakeAccessCache(), limits, Clock.systemUTC(), tenantId -> 1L);
        limited.check("t", "u", "chat", "req-1");
        assertThatThrownBy(() -> limited.check("t", "u", "chat", "req-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);

        QuotaGuard open = new QuotaGuard(new FakeAccessCache(), limits, Clock.systemUTC(), tenantId -> null);
        open.check("t", "u", "chat", "req-1");
        open.check("t", "u", "chat", "req-2");
    }

    /** 登记写入 REQUESTS / DAILY。重复标识按冲突处理，查不到行则没有单独上限。 */
    @Test
    void registerAndReadDailyRequests() {
        TenantQuotaMapper quotas = mock(TenantQuotaMapper.class);
        MysqlTenantQuota directory = new MysqlTenantQuota(quotas);

        assertThat(directory.setDailyRequests("t", 10L).limit()).isEqualTo(10L);
        verify(quotas).insert(any(TenantQuotaRecord.class));

        when(quotas.insert(any(TenantQuotaRecord.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> directory.setDailyRequests("t", 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        when(quotas.findDailyRequestLimit("t")).thenReturn(10L);
        assertThat(directory.dailyRequestLimit("t")).isEqualTo(10L);
        when(quotas.findDailyRequestLimit("missing")).thenReturn(null);
        assertThat(directory.dailyRequestLimit("missing")).isNull();
    }

    /** 只改已有行。没有这一行按找不到。上限 0 表示不限额。 */
    @Test
    void updateChangesExistingDailyRequests() {
        TenantQuotaMapper quotas = mock(TenantQuotaMapper.class);
        MysqlTenantQuota directory = new MysqlTenantQuota(quotas);

        when(quotas.updateDailyRequestLimit("t", 20L)).thenReturn(0);
        assertThatThrownBy(() -> directory.updateDailyRequests("t", 20L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        when(quotas.updateDailyRequestLimit("t", 0L)).thenReturn(1);
        assertThat(directory.updateDailyRequests("t", 0L)).satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.quotaType()).isEqualTo("REQUESTS");
            assertThat(saved.period()).isEqualTo("DAILY");
            assertThat(saved.limit()).isEqualTo(0L);
        });
        verify(quotas).updateDailyRequestLimit("t", 0L);
        verify(quotas, never()).insert(any(TenantQuotaRecord.class));
    }
}
