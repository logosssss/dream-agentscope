package com.zhu.cache.api;

import com.zhu.cache.api.bloom.BloomFilter;
import com.zhu.cache.api.counter.CounterCache;
import com.zhu.cache.api.kv.KvCache;
import com.zhu.cache.api.lock.DistributedLock;
import com.zhu.cache.api.lock.TokenLock;
import com.zhu.cache.api.rate.RateLimiter;

/**
 * 缓存能力组合门面（C1）。业务可只注入本接口；亦可按需注入各小接口。
 * 二期追加 {@link RateLimiter} / {@link BloomFilter} 形状，默认不接线业务。
 */
public interface CacheFacade {

    /** 计数。 */
    CounterCache counters();

    /** 短文本 KV。 */
    KvCache kv();

    /** SETNX 短锁 / 幂等占位。 */
    TokenLock tokenLocks();

    /** Redisson 分布式锁。 */
    DistributedLock distributedLocks();

    /** 分布式限流（形状；业务接线另块）。 */
    RateLimiter rateLimiters();

    /** 布隆过滤器（形状；业务接线另块）。 */
    BloomFilter bloomFilters();
}
