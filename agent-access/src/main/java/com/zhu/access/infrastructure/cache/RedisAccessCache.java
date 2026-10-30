package com.zhu.access.infrastructure.cache;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 计数。键空间只用于额度、幂等和限流，不写入 AgentScope 的状态或快照。
 */
public class RedisAccessCache implements AccessCache {

    private final StringRedisTemplate redis;

    public RedisAccessCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 第一次加到 1 时设置过期。进程在 INCR 和 EXPIRE 之间退出会留下没有过期的键，下次写入会继续累加。
     */
    @Override
    public long increment(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        long current = value == null ? 1L : value;
        if (current == 1L) {
            redis.expire(key, ttl);
        }
        return current;
    }

    /** SETNX 带过期，占住失败说明窗口内已经处理过这个键。 */
    @Override
    public boolean claim(String key, Duration ttl) {
        Boolean claimed = redis.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(claimed);
    }
}
