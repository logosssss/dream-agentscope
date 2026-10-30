package com.zhu.access.infrastructure.cache;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis 计数和会话短锁。不写入 AgentScope 的状态或快照。
 */
public class RedisAccessCache implements AccessCache {

    /** 比对令牌再删除，避免一次调用的收尾删掉下一次已经占上的锁。 */
    private static final RedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

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

    /** 令牌作为值。过期后键自己消失，崩溃的进程不会一直占着会话。 */
    @Override
    public boolean lock(String key, String token, Duration ttl) {
        Boolean claimed = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(claimed);
    }

    /** 令牌不符时脚本返回 0，键留着。 */
    @Override
    public void unlock(String key, String token) {
        redis.execute(UNLOCK, List.of(key), token);
    }
}
