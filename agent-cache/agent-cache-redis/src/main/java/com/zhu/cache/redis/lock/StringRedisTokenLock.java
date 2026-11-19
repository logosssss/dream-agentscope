package com.zhu.cache.redis.lock;

import com.zhu.cache.api.lock.TokenLock;
import com.zhu.cache.redis.RedisCallSupport;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * SETNX 短锁。令牌比对删除，避免误删他人锁。
 */
public class StringRedisTokenLock implements TokenLock {

    /** 比对令牌再删除。 */
    private static final RedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    /**
     * @param redis 已配置的字符串模板
     */
    public StringRedisTokenLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean claim(String key, Duration ttl) {
        return RedisCallSupport.call(() -> Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl)));
    }

    @Override
    public boolean lock(String key, String token, Duration ttl) {
        return RedisCallSupport.call(() -> Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl)));
    }

    @Override
    public void unlock(String key, String token) {
        RedisCallSupport.call(() -> redis.execute(UNLOCK, List.of(key), token));
    }
}
