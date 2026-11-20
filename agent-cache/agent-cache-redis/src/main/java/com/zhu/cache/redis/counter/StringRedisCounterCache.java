package com.zhu.cache.redis.counter;

import com.zhu.cache.api.counter.CounterCache;
import com.zhu.cache.redis.RedisCallSupport;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 {@link StringRedisTemplate} 的计数实现。语义对齐原 access {@code RedisAccessCache}。
 */
public class StringRedisCounterCache implements CounterCache {

    private final StringRedisTemplate redis;

    /**
     * @param redis 已配置的字符串模板；勿传 null
     */
    public StringRedisCounterCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttl) {
        return incrementBy(key, 1L, ttl);
    }

    @Override
    public long incrementBy(String key, long delta, Duration ttl) {
        if (delta < 1L) {
            return getCounter(key);
        }
        return RedisCallSupport.call(() -> {
            Long value = redis.opsForValue().increment(key, delta);
            long current = value == null ? delta : value;
            if (current == delta) {
                redis.expire(key, ttl);
            }
            return current;
        });
    }

    @Override
    public long add(String key, long delta, Duration ttl) {
        if (delta == 0L) {
            return getCounter(key);
        }
        return RedisCallSupport.call(() -> {
            Long value = redis.opsForValue().increment(key, delta);
            long current = value == null ? delta : value;
            if (current < 0L) {
                redis.opsForValue().set(key, "0", ttl);
                return 0L;
            }
            redis.expire(key, ttl);
            return current;
        });
    }

    @Override
    public long getCounter(String key) {
        return RedisCallSupport.call(() -> {
            String raw = redis.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return 0L;
            }
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ex) {
                return 0L;
            }
        });
    }
}
