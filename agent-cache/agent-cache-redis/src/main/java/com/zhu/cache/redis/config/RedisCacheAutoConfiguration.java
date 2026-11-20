package com.zhu.cache.redis.config;

import com.zhu.cache.api.CacheFacade;
import com.zhu.cache.api.bloom.BloomFilter;
import com.zhu.cache.api.counter.CounterCache;
import com.zhu.cache.api.kv.KvCache;
import com.zhu.cache.api.lock.DistributedLock;
import com.zhu.cache.api.lock.TokenLock;
import com.zhu.cache.api.rate.RateLimiter;
import com.zhu.cache.redis.DefaultCacheFacade;
import com.zhu.cache.redis.bloom.RedissonBloomFilter;
import com.zhu.cache.redis.counter.StringRedisCounterCache;
import com.zhu.cache.redis.kv.StringRedisKvCache;
import com.zhu.cache.redis.lock.RedissonDistributedLock;
import com.zhu.cache.redis.lock.StringRedisTokenLock;
import com.zhu.cache.redis.rate.RedissonRateLimiter;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 族自动配置。仅 {@code agentscope.cache.redis.enabled=true} 时生效；主机必填。
 * 同时装配 Lettuce 与 Redisson，共享同一套 host/port/password。
 */
@AutoConfiguration
@EnableConfigurationProperties(RedisCacheProperties.class)
@ConditionalOnProperty(prefix = "agentscope.cache.redis", name = "enabled", havingValue = "true")
public class RedisCacheAutoConfiguration {

    /**
     * Lettuce 连接工厂。主机为空拒绝启动。
     */
    @Bean(name = "cacheRedisConnectionFactory")
    @ConditionalOnMissingBean(name = "cacheRedisConnectionFactory")
    public LettuceConnectionFactory cacheRedisConnectionFactory(RedisCacheProperties properties) {
        requireHost(properties);
        RedisStandaloneConfiguration standalone =
                new RedisStandaloneConfiguration(properties.getHost().trim(), properties.getPort());
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            standalone.setPassword(RedisPassword.of(properties.getPassword()));
        }
        return new LettuceConnectionFactory(standalone);
    }

    /**
     * 字符串模板，供计数 / KV / TokenLock。
     */
    @Bean(name = "cacheStringRedisTemplate")
    @ConditionalOnMissingBean(name = "cacheStringRedisTemplate")
    public StringRedisTemplate cacheStringRedisTemplate(
            @Qualifier("cacheRedisConnectionFactory") LettuceConnectionFactory cacheRedisConnectionFactory) {
        return new StringRedisTemplate(cacheRedisConnectionFactory);
    }

    /**
     * Redisson 客户端。地址格式 {@code redis://host:port}；有密码则 setPassword。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient cacheRedissonClient(RedisCacheProperties properties) {
        requireHost(properties);
        Config config = new Config();
        String address = "redis://" + properties.getHost().trim() + ":" + properties.getPort();
        var server = config.useSingleServer().setAddress(address);
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            server.setPassword(properties.getPassword());
        }
        return Redisson.create(config);
    }

    /** 计数。 */
    @Bean
    @ConditionalOnMissingBean(CounterCache.class)
    public CounterCache counterCache(
            @Qualifier("cacheStringRedisTemplate") StringRedisTemplate cacheStringRedisTemplate) {
        return new StringRedisCounterCache(cacheStringRedisTemplate);
    }

    /** KV。 */
    @Bean
    @ConditionalOnMissingBean(KvCache.class)
    public KvCache kvCache(@Qualifier("cacheStringRedisTemplate") StringRedisTemplate cacheStringRedisTemplate) {
        return new StringRedisKvCache(cacheStringRedisTemplate);
    }

    /** SETNX 短锁。 */
    @Bean
    @ConditionalOnMissingBean(TokenLock.class)
    public TokenLock tokenLock(
            @Qualifier("cacheStringRedisTemplate") StringRedisTemplate cacheStringRedisTemplate) {
        return new StringRedisTokenLock(cacheStringRedisTemplate);
    }

    /** Redisson 分布式锁。 */
    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock distributedLock(RedissonClient cacheRedissonClient) {
        return new RedissonDistributedLock(cacheRedissonClient);
    }

    /** Redisson 限流（形状；本轮无业务接线）。 */
    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter rateLimiter(RedissonClient cacheRedissonClient) {
        return new RedissonRateLimiter(cacheRedissonClient);
    }

    /** Redisson 布隆（形状；本轮无业务接线）。 */
    @Bean
    @ConditionalOnMissingBean(BloomFilter.class)
    public BloomFilter bloomFilter(RedissonClient cacheRedissonClient) {
        return new RedissonBloomFilter(cacheRedissonClient);
    }

    /** 组合门面。 */
    @Bean
    @ConditionalOnMissingBean(CacheFacade.class)
    public CacheFacade cacheFacade(
            CounterCache counterCache,
            KvCache kvCache,
            TokenLock tokenLock,
            DistributedLock distributedLock,
            RateLimiter rateLimiter,
            BloomFilter bloomFilter) {
        return new DefaultCacheFacade(
                counterCache, kvCache, tokenLock, distributedLock, rateLimiter, bloomFilter);
    }

    private static void requireHost(RedisCacheProperties properties) {
        if (properties.getHost() == null || properties.getHost().isBlank()) {
            throw new IllegalStateException("agentscope.cache.redis.host is required when redis is enabled");
        }
    }
}
