package com.zhu.access.application;

import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.audit.api.DailyCostAccumulator;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/**
 * 把当日费用微单位累加进 Redis。键与 QuotaGuard 校验共用。
 */
@Service
public class RedisDailyCostAccumulator implements DailyCostAccumulator {

    private static final Logger log = Logger.getLogger(RedisDailyCostAccumulator.class.getName());

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final AccessCache cache;

    private final Clock clock;

    /** 唯一构造。 */
    public RedisDailyCostAccumulator(AccessCache cache) {
        this.cache = cache;
        this.clock = Clock.systemUTC();
    }

    /**
     * 失败只打警告。不能让已经生成的回复变失败。
     */
    @Override
    public void add(String tenantId, String userId, long micros) {
        if (micros < 1L) {
            return;
        }
        try {
            String day = DAY.format(clock.instant());
            cache.incrementBy(QuotaGuard.costKey(tenantId, userId, day), micros, QuotaGuard.QUOTA_TTL);
        } catch (RuntimeException ex) {
            log.warning("daily cost accumulate failed");
        }
    }
}
