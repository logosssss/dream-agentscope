package com.zhu.access.application.account;

import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.audit.api.DailyTokenAccumulator;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/**
 * 把当日 token 累加进 Redis。键与 QuotaGuard 校验共用。
 */
@Service
public class RedisDailyTokenAccumulator implements DailyTokenAccumulator {

    private static final Logger log = Logger.getLogger(RedisDailyTokenAccumulator.class.getName());

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final AccessCache cache;

    private final Clock clock;

    /** 唯一构造。测试要拨时钟时自行包装 AccessCache。 */
    public RedisDailyTokenAccumulator(AccessCache cache) {
        this.cache = cache;
        this.clock = Clock.systemUTC();
    }

    /**
     * 失败只打警告。不能让已经生成的回复变失败。
     */
    @Override
    public void add(String tenantId, String userId, int tokens) {
        if (tokens < 1) {
            return;
        }
        try {
            String day = DAY.format(clock.instant());
            cache.incrementBy(QuotaGuard.tokenKey(tenantId, userId, day), tokens, QuotaGuard.QUOTA_TTL);
        } catch (RuntimeException ex) {
            log.warning("daily token accumulate failed");
        }
    }
}
