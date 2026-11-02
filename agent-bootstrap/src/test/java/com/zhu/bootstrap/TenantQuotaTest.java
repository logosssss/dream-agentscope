package com.zhu.bootstrap;

import com.zhu.access.application.ConcurrentRunGuard;
import com.zhu.access.application.QuotaGuard;
import com.zhu.access.application.RedisDailyCostAccumulator;
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
 * 租户每日请求、token、费用与同时运行上限。列表不带已用量。不连接 MySQL 或 Redis。
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

    /** 已用量达到费用微单位上限后下一次对话拒绝。 */
    @Test
    void dailyCostBlocksAfterUsage() {
        FakeAccessCache cache = new FakeAccessCache();
        AccessLimitProperties limits = new AccessLimitProperties();
        TenantQuotaLookup quotas = new TenantQuotaLookup() {
            @Override
            public Long dailyRequestLimit(String tenantId) {
                return null;
            }

            @Override
            public Long dailyCostLimit(String tenantId) {
                return 6000L;
            }
        };
        QuotaGuard guard = new QuotaGuard(cache, limits, Clock.systemUTC(), quotas);
        guard.check("t", "u", "chat", "req-1");
        new RedisDailyCostAccumulator(cache).add("t", "u", 6000L);
        assertThatThrownBy(() -> guard.check("t", "u", "chat", "req-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);
    }

    /** 已占用到同时运行上限后下一次对话拒绝；结束后可以再进。 */
    @Test
    void concurrentRunsBlockUntilReleased() {
        FakeAccessCache cache = new FakeAccessCache();
        TenantQuotaLookup quotas = new TenantQuotaLookup() {
            @Override
            public Long dailyRequestLimit(String tenantId) {
                return null;
            }

            @Override
            public Long concurrentRunLimit(String tenantId) {
                return 1L;
            }
        };
        ConcurrentRunGuard guard = new ConcurrentRunGuard(cache, 120, quotas);
        ConcurrentRunGuard.Slot first = guard.open("t");
        assertThatThrownBy(() -> guard.open("t"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);
        guard.close(first);
        ConcurrentRunGuard.Slot second = guard.open("t");
        assertThat(second.key()).isEqualTo("concurrent:t");
        guard.close(second);
    }

    /** 没有行时不占 Redis。释放空槽没有副作用。 */
    @Test
    void concurrentRunsSkipWhenNoRow() {
        FakeAccessCache cache = new FakeAccessCache();
        ConcurrentRunGuard guard = new ConcurrentRunGuard(cache, 120, requestsOnly(null));
        ConcurrentRunGuard.Slot slot = guard.open("t");
        assertThat(slot.key()).isNull();
        guard.close(slot);
        assertThat(cache.getCounter("concurrent:t")).isZero();
    }

    /** 登记 CONCURRENT_RUNS / CURRENT，列表单独读。 */
    @Test
    void registerAndListConcurrentRuns() {
        TenantQuotaMapper quotas = mock(TenantQuotaMapper.class);
        MysqlTenantQuota directory = new MysqlTenantQuota(quotas);
        assertThat(directory.setConcurrentRuns("t", 3L)).satisfies(saved -> {
            assertThat(saved.quotaType()).isEqualTo("CONCURRENT_RUNS");
            assertThat(saved.period()).isEqualTo("CURRENT");
            assertThat(saved.limit()).isEqualTo(3L);
        });
        when(quotas.findConcurrentRunLimit("t")).thenReturn(3L);
        assertThat(directory.concurrentRunLimit("t")).isEqualTo(3L);
        when(quotas.updateConcurrentRunLimit("t", 5L)).thenReturn(1);
        assertThat(directory.updateConcurrentRuns("t", 5L).period()).isEqualTo("CURRENT");
        when(quotas.recentConcurrent(50)).thenReturn(List.of());
        assertThat(directory.recentConcurrent(50)).isEmpty();
    }

    /** 登记 COST / DAILY，列表单独读。 */
    @Test
    void registerAndListDailyCost() {
        TenantQuotaMapper quotas = mock(TenantQuotaMapper.class);
        MysqlTenantQuota directory = new MysqlTenantQuota(quotas);
        assertThat(directory.setDailyCost("t", 1_000_000L).quotaType()).isEqualTo("COST");
        when(quotas.findDailyCostLimit("t")).thenReturn(1_000_000L);
        assertThat(directory.dailyCostLimit("t")).isEqualTo(1_000_000L);
        when(quotas.updateDailyCostLimit("t", 2_000_000L)).thenReturn(1);
        assertThat(directory.updateDailyCost("t", 2_000_000L).limit()).isEqualTo(2_000_000L);
        when(quotas.recentCosts(50)).thenReturn(List.of());
        assertThat(directory.recentCosts(50)).isEmpty();
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
