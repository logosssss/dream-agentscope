package com.zhu.cache.redis.kv;

import com.zhu.cache.api.kv.KvCache;
import com.zhu.cache.redis.RedisCallSupport;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 {@link StringRedisTemplate} 的短文本 KV。
 */
public class StringRedisKvCache implements KvCache {

    private final StringRedisTemplate redis;

    /**
     * @param redis 已配置的字符串模板
     */
    public StringRedisKvCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String get(String key) {
        return RedisCallSupport.call(() -> redis.opsForValue().get(key));
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        RedisCallSupport.run(() -> redis.opsForValue().set(key, value, ttl));
    }

    @Override
    public void delete(String key) {
        RedisCallSupport.run(() -> redis.delete(key));
    }
}
