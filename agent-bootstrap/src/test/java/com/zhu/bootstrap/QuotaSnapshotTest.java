package com.zhu.bootstrap;

import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.audit.api.TenantQuotaLookup;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 额度快照只读 Redis 计数。不连接 Redis，也不扣额度。
 */
class QuotaSnapshotTest {

    /** 租户行覆盖进程请求上限。token / 费用 / 同时运行没有行时上限为空。 */
    @Test
    void snapshotReadsTenantLimitsAndCounters() {
        FakeAccessCache cache = new FakeAccessCache();
        Clock clock = Clock.fixed(Instant.parse("2026-10-30T02:03:00Z"), ZoneOffset.UTC);
        TenantQuotaLookup quotas = mock(TenantQuotaLookup.class);
        when(quotas.dailyRequestLimit("t")).thenReturn(5L);
        when(quotas.dailyTokenLimit("t")).thenReturn(100L);
        when(quotas.dailyCostLimit("t")).thenReturn(1_000_000L);
        when(quotas.concurrentRunLimit("t")).thenReturn(2L);
        cache.incrementBy(QuotaGuard.tokenKey("t", "u", "20261030"), 7L, QuotaGuard.QUOTA_TTL);
        cache.incrementBy(QuotaGuard.costKey("t", "u", "20261030"), 20L, QuotaGuard.QUOTA_TTL);
        cache.incrementBy(QuotaGuard.concurrentKey("t"), 1L, QuotaGuard.QUOTA_TTL);
        QuotaGuard guard = new QuotaGuard(cache, new AccessLimitProperties(), clock, quotas);
        assertThat(guard.snapshot("t", "u")).satisfies(snap -> {
            assertThat(snap.requests().limit()).isEqualTo(5L);
            assertThat(snap.tokens().used()).isEqualTo(7L);
            assertThat(snap.tokens().limit()).isEqualTo(100L);
            assertThat(snap.cost().used()).isEqualTo(20L);
            assertThat(snap.concurrent().used()).isEqualTo(1L);
            assertThat(snap.concurrent().limit()).isEqualTo(2L);
        });
        assertThat(guard.snapshot("t", "u").tokens().used()).isEqualTo(7L);
    }

    /** Redis 读失败按不可用，不改走内存。 */
    @Test
    void snapshotPropagatesUnavailable() {
        AccessCache cache = mock(AccessCache.class);
        when(cache.getCounter(anyString()))
                .thenThrow(new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable"));
        QuotaGuard guard = new QuotaGuard(cache, new AccessLimitProperties(), Clock.systemUTC());
        assertThatThrownBy(() -> guard.snapshot("t", "u"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.UNAVAILABLE);
    }
}
