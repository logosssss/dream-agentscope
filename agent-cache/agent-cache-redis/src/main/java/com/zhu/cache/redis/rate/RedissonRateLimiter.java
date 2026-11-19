package com.zhu.cache.redis.rate;

import com.zhu.cache.api.rate.RateLimiter;
import com.zhu.cache.redis.RedisCallSupport;
import java.time.Duration;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

/**
 * Redis 族：基于 Redisson {@link RRateLimiter} 的限流实现。
 * 每次 tryAcquire 前 trySetRate，便于热改速率；失败按 E1。
 */
public class RedissonRateLimiter implements RateLimiter {

    private final RedissonClient redisson;

    /**
     * @param redisson 已连上的客户端
     */
    public RedissonRateLimiter(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public boolean tryAcquire(String key, long rate, Duration rateInterval) {
        if (key == null || key.isBlank() || rate < 1L || rateInterval == null || rateInterval.isZero()
                || rateInterval.isNegative()) {
            return false;
        }
        return RedisCallSupport.call(() -> {
            RRateLimiter limiter = redisson.getRateLimiter(key);
            long seconds = Math.max(1L, rateInterval.toSeconds());
            limiter.trySetRate(RateType.OVERALL, rate, seconds, RateIntervalUnit.SECONDS);
            return limiter.tryAcquire(1);
        });
    }
}
