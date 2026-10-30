package com.zhu.bootstrap;

import com.zhu.access.application.QuotaGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.infrastructure.cache.MemoryAccessCache;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 额度用进程内计数验证。限额为 0 时不拦截，重复请求标识在十分钟内拒绝。
 */
class QuotaGuardTest {

    /** 当日第二次超过上限。同一请求标识的第二次直接算重复，不再扣额度。 */
    @Test
    void dailyLimitAndDuplicateRequest() {
        AccessLimitProperties limits = new AccessLimitProperties();
        limits.setDailyLimit(1);
        QuotaGuard guard = guard(limits, Clock.systemUTC());

        guard.check("tenant", "user", "chat", "req-1");

        BusinessException duplicate = caught(() -> guard.check("tenant", "user", "chat", "req-1"));
        assertThat(duplicate.errorCode()).isEqualTo(ErrorCode.CONFLICT);

        BusinessException overQuota = caught(() -> guard.check("tenant", "user", "chat", "req-2"));
        assertThat(overQuota.errorCode()).isEqualTo(ErrorCode.QUOTA_EXCEEDED);
    }

    /** 拨到下一分钟后，上一分钟的限流计数不再算。 */
    @Test
    void rateLimitResetsOnNextMinute() {
        AccessLimitProperties limits = new AccessLimitProperties();
        limits.setPerMinute(1);
        MutableClock clock = new MutableClock(Instant.parse("2026-10-30T02:03:00Z"));
        QuotaGuard guard = guard(limits, clock);

        guard.check("tenant", "user", "chat", null);
        assertThatThrownBy(() -> guard.check("tenant", "user", "chat", null))
                .isInstanceOf(BusinessException.class);

        clock.instant = Instant.parse("2026-10-30T02:04:00Z");
        guard.check("tenant", "user", "chat", null);
    }

    /** 计数和判断用同一只时钟，拨分钟时两边一起动。 */
    private static QuotaGuard guard(AccessLimitProperties limits, Clock clock) {
        return new QuotaGuard(new MemoryAccessCache(clock), limits, clock);
    }

    /** 取出业务异常，方便断言错误码。 */
    private static BusinessException caught(Runnable call) {
        try {
            call.run();
        } catch (BusinessException error) {
            return error;
        }
        throw new AssertionError("expected BusinessException");
    }

    /** 测试里手动拨动的时钟。 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        /** 额度按 UTC 分日和分分钟。 */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** 测试不换时区。 */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /** 当前被拨到的时刻。 */
        @Override
        public Instant instant() {
            return instant;
        }
    }
}
