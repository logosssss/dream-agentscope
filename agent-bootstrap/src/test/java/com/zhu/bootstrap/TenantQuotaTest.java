package com.zhu.bootstrap;

import com.zhu.access.application.QuotaGuard;
import com.zhu.access.application.RedisDailyTokenAccumulator;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.audit.api.TenantQuotaLookup;
import com.zhu.audit.application.MysqlTenantQuota;
import com.zhu.audit.infrastructure.persistence.TenantQuotaMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantQuotaRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Clock;
import java.util.List;
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
 * 租户每日请求与 token 上限。列表不带已用量。不连接 MySQL 或 Redis。进程配置为 0 时，有行才限额。
 */
class TenantQuotaTest {

    /** 有行时第二次超过这一行。没有行时不拦截。 */
    @Test
    void dailyRequestsFollowTheTenantRow() {
        AccessLimitProperties limits = new AccessLimitProperties();
        QuotaGuard limited = new QuotaGuard(new FakeAccessCache(), limits, Clock.systemUTC(), requestsOnly(1L));
        limited.check("t", "u", "chat", "req-1");
        assertThatThrownBy(() -> limited.check("t", "u", "chat", "req-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);

        QuotaGuard open = new QuotaGuard(new FakeAccessCache(), limits, Clock.systemUTC(), requestsOnly(null));
        open.check("t", "u", "chat", "req-1");
        open.check("t", "u", "chat", "req-2");
    }

    /** 已用量达到 token 上限后下一次对话拒绝。记账累加与校验共用键。 */
    @Test
    void dailyTokensBlockAfterUsage() {
        FakeAccessCache cache = new FakeAccessCache();
        AccessLimitProperties limits = new AccessLimitProperties();
        TenantQuotaLookup quotas = new TenantQuotaLookup() {
            @Override
            public Long dailyRequestLimit(String tenantId) {
                return null;
            }

            @Override
            public Long dailyTokenLimit(String tenantId) {
                return 10L;
            }
        };
        QuotaGuard guard = new QuotaGuard(cache, limits, Clock.systemUTC(), quotas);
        guard.check("t", "u", "chat", "req-1");
        // 测试构造在 access 同模块外不可见，走公开构造即可。
        new RedisDailyTokenAccumulator(cache).add("t", "u", 10);
        assertThatThrownBy(() -> guard.check("t", "u", "chat", "req-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);
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

    /** 列表只看每日请求上限。空结果原样返回。 */
    @Test
    void recentListsDailyRequests() {
        TenantQuotaMapper quotas = mock(TenantQuotaMapper.class);
        MysqlTenantQuota directory = new MysqlTenantQuota(quotas);
        when(quotas.recent(50)).thenReturn(List.of());
        assertThat(directory.recent(50)).isEmpty();

        TenantQuotaRecord row = new TenantQuotaRecord();
        row.setTenantId("t");
        row.setQuotaType("REQUESTS");
        row.setPeriod("DAILY");
        row.setLimitValue(0L);
        when(quotas.recent(50)).thenReturn(List.of(row));
        assertThat(directory.recent(0)).singleElement().satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.quotaType()).isEqualTo("REQUESTS");
            assertThat(saved.period()).isEqualTo("DAILY");
            assertThat(saved.limit()).isEqualTo(0L);
        });
    }

    /** 登记 TOKENS / DAILY，列表单独读。 */
    @Test
    void registerAndListDailyTokens() {
        TenantQuotaMapper quotas = mock(TenantQuotaMapper.class);
        MysqlTenantQuota directory = new MysqlTenantQuota(quotas);
        assertThat(directory.setDailyTokens("t", 1000L).quotaType()).isEqualTo("TOKENS");
        when(quotas.findDailyTokenLimit("t")).thenReturn(1000L);
        assertThat(directory.dailyTokenLimit("t")).isEqualTo(1000L);
        when(quotas.updateDailyTokenLimit("t", 2000L)).thenReturn(1);
        assertThat(directory.updateDailyTokens("t", 2000L).limit()).isEqualTo(2000L);
        when(quotas.recentTokens(50)).thenReturn(List.of());
        assertThat(directory.recentTokens(50)).isEmpty();
    }

    private static TenantQuotaLookup requestsOnly(Long limit) {
        return new TenantQuotaLookup() {
            @Override
            public Long dailyRequestLimit(String tenantId) {
                return limit;
            }
        };
    }
}
