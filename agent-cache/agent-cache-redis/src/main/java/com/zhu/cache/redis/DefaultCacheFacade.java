package com.zhu.cache.redis;

import com.zhu.cache.api.CacheFacade;
import com.zhu.cache.api.bloom.BloomFilter;
import com.zhu.cache.api.counter.CounterCache;
import com.zhu.cache.api.kv.KvCache;
import com.zhu.cache.api.lock.DistributedLock;
import com.zhu.cache.api.lock.TokenLock;
import com.zhu.cache.api.rate.RateLimiter;

/**
 * Redis 族默认组合门面。持有各小接口引用，不做额外逻辑。
 */
public class DefaultCacheFacade implements CacheFacade {

    private final CounterCache counters;
    private final KvCache kv;
    private final TokenLock tokenLocks;
    private final DistributedLock distributedLocks;
    private final RateLimiter rateLimiters;
    private final BloomFilter bloomFilters;

    /**
     * @param counters         计数
     * @param kv               KV
     * @param tokenLocks       短锁
     * @param distributedLocks Redisson 锁
     * @param rateLimiters     限流
     * @param bloomFilters     布隆
     */
    public DefaultCacheFacade(
            CounterCache counters,
            KvCache kv,
            TokenLock tokenLocks,
            DistributedLock distributedLocks,
            RateLimiter rateLimiters,
            BloomFilter bloomFilters) {
        this.counters = counters;
        this.kv = kv;
        this.tokenLocks = tokenLocks;
        this.distributedLocks = distributedLocks;
        this.rateLimiters = rateLimiters;
        this.bloomFilters = bloomFilters;
    }

    @Override
    public CounterCache counters() {
        return counters;
    }

    @Override
    public KvCache kv() {
        return kv;
    }

    @Override
    public TokenLock tokenLocks() {
        return tokenLocks;
    }

    @Override
    public DistributedLock distributedLocks() {
        return distributedLocks;
    }

    @Override
    public RateLimiter rateLimiters() {
        return rateLimiters;
    }

    @Override
    public BloomFilter bloomFilters() {
        return bloomFilters;
    }
}
