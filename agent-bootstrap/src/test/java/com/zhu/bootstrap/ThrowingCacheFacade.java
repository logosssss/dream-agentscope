package com.zhu.bootstrap;

import com.zhu.cache.api.CacheFacade;
import com.zhu.cache.api.bloom.BloomFilter;
import com.zhu.cache.api.counter.CounterCache;
import com.zhu.cache.api.kv.KvCache;
import com.zhu.cache.api.lock.DistributedLock;
import com.zhu.cache.api.lock.TokenLock;
import com.zhu.cache.api.rate.RateLimiter;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Duration;

/**
 * 测试用：所有缓存能力一律不可用。
 */
final class ThrowingCacheFacade implements CacheFacade {

    private final CounterCache counters = new ThrowingCounters();
    private final KvCache kv = new ThrowingKv();
    private final TokenLock tokens = new ThrowingTokens();
    private final DistributedLock locks = new ThrowingLocks();
    private final RateLimiter rates = new ThrowingRates();
    private final BloomFilter blooms = new ThrowingBlooms();

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
        return tokens;
    }

    @Override
    public DistributedLock distributedLocks() {
        return locks;
    }

    @Override
    public RateLimiter rateLimiters() {
        return rates;
    }

    @Override
    public BloomFilter bloomFilters() {
        return blooms;
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
    }

    private static final class ThrowingCounters implements CounterCache {
        @Override
        public long increment(String key, Duration ttl) {
            throw unavailable();
        }

        @Override
        public long incrementBy(String key, long delta, Duration ttl) {
            throw unavailable();
        }

        @Override
        public long add(String key, long delta, Duration ttl) {
            throw unavailable();
        }

        @Override
        public long getCounter(String key) {
            throw unavailable();
        }
    }

    private static final class ThrowingKv implements KvCache {
        @Override
        public String get(String key) {
            throw unavailable();
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            throw unavailable();
        }

        @Override
        public void delete(String key) {
            throw unavailable();
        }
    }

    private static final class ThrowingTokens implements TokenLock {
        @Override
        public boolean claim(String key, Duration ttl) {
            throw unavailable();
        }

        @Override
        public boolean lock(String key, String token, Duration ttl) {
            throw unavailable();
        }

        @Override
        public void unlock(String key, String token) {
            throw unavailable();
        }
    }

    private static final class ThrowingLocks implements DistributedLock {
        @Override
        public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
            throw unavailable();
        }

        @Override
        public void unlock(String key) {
            throw unavailable();
        }
    }

    private static final class ThrowingRates implements RateLimiter {
        @Override
        public boolean tryAcquire(String key, long rate, Duration rateInterval) {
            throw unavailable();
        }
    }

    private static final class ThrowingBlooms implements BloomFilter {
        @Override
        public boolean tryInit(String name, long expectedInsertions, double falsePositiveProbability) {
            throw unavailable();
        }

        @Override
        public void add(String name, String value) {
            throw unavailable();
        }

        @Override
        public boolean mightContain(String name, String value) {
            throw unavailable();
        }
    }
}
