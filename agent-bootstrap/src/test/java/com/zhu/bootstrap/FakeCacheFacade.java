package com.zhu.bootstrap;

import com.zhu.cache.api.CacheFacade;
import com.zhu.cache.api.bloom.BloomFilter;
import com.zhu.cache.api.counter.CounterCache;
import com.zhu.cache.api.kv.KvCache;
import com.zhu.cache.api.lock.DistributedLock;
import com.zhu.cache.api.lock.TokenLock;
import com.zhu.cache.api.rate.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 测试用缓存门面。只在测试源码；生产由 Redis 族自动配置提供。
 * 限流为固定窗口计数；布隆用精确集合替身（非概率结构）。
 */
public class FakeCacheFacade implements CacheFacade {

    private final Clock clock;
    private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Hold> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> blooms = new ConcurrentHashMap<>();
    private final CounterCache counters = new Counters();
    private final KvCache kv = new Kv();
    private final TokenLock tokenLocks = new Tokens();
    private final DistributedLock distributedLocks = new Dist();
    private final RateLimiter rateLimiters = new Rates();
    private final BloomFilter bloomFilters = new Blooms();

    /** 系统时钟。 */
    public FakeCacheFacade() {
        this(Clock.systemUTC());
    }

    /** 可拨时钟，验证过期后计数从 1 再起。 */
    public FakeCacheFacade(Clock clock) {
        this.clock = clock;
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

    private final class Counters implements CounterCache {
        @Override
        public long increment(String key, Duration ttl) {
            return incrementBy(key, 1L, ttl);
        }

        @Override
        public long incrementBy(String key, long delta, Duration ttl) {
            if (delta < 1L) {
                return getCounter(key);
            }
            long now = clock.millis();
            Slot slot = slots.compute(key, (name, current) -> {
                if (current == null || current.expiresAtMillis <= now) {
                    return new Slot(delta, now + ttl.toMillis(), null);
                }
                return new Slot(current.value + delta, current.expiresAtMillis, current.text);
            });
            return slot.value;
        }

        @Override
        public long add(String key, long delta, Duration ttl) {
            if (delta == 0L) {
                return getCounter(key);
            }
            long now = clock.millis();
            Slot slot = slots.compute(key, (name, current) -> {
                long next = (current == null || current.expiresAtMillis <= now) ? delta : current.value + delta;
                if (next < 0L) {
                    next = 0L;
                }
                String text = current == null ? null : current.text;
                return new Slot(next, now + ttl.toMillis(), text);
            });
            return slot.value;
        }

        @Override
        public long getCounter(String key) {
            long now = clock.millis();
            Slot slot = slots.get(key);
            if (slot == null || slot.expiresAtMillis <= now) {
                return 0L;
            }
            return slot.value;
        }
    }

    private final class Kv implements KvCache {
        @Override
        public String get(String key) {
            long now = clock.millis();
            Slot slot = slots.get(key);
            if (slot == null || slot.expiresAtMillis <= now || slot.text == null) {
                return null;
            }
            return slot.text;
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            long now = clock.millis();
            slots.put(key, new Slot(0, now + ttl.toMillis(), value));
        }

        @Override
        public void delete(String key) {
            slots.remove(key);
        }
    }

    private final class Tokens implements TokenLock {
        @Override
        public boolean claim(String key, Duration ttl) {
            long now = clock.millis();
            AtomicBoolean won = new AtomicBoolean(false);
            slots.compute(key, (name, current) -> {
                if (current == null || current.expiresAtMillis <= now) {
                    won.set(true);
                    return new Slot(1, now + ttl.toMillis(), null);
                }
                return current;
            });
            return won.get();
        }

        @Override
        public boolean lock(String key, String token, Duration ttl) {
            long now = clock.millis();
            AtomicBoolean won = new AtomicBoolean(false);
            locks.compute(key, (name, current) -> {
                if (current == null || current.expiresAtMillis <= now) {
                    won.set(true);
                    return new Hold(token, now + ttl.toMillis());
                }
                return current;
            });
            return won.get();
        }

        @Override
        public void unlock(String key, String token) {
            long now = clock.millis();
            locks.compute(key, (name, current) -> {
                if (current == null || current.expiresAtMillis <= now) {
                    return null;
                }
                if (token != null && token.equals(current.token)) {
                    return null;
                }
                return current;
            });
        }
    }

    private static final class Dist implements DistributedLock {
        private final ConcurrentHashMap<String, Thread> holders = new ConcurrentHashMap<>();

        @Override
        public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
            return holders.putIfAbsent(key, Thread.currentThread()) == null
                    || holders.get(key) == Thread.currentThread();
        }

        @Override
        public void unlock(String key) {
            holders.compute(key, (name, holder) -> holder == Thread.currentThread() ? null : holder);
        }
    }

    private final class Rates implements RateLimiter {
        @Override
        public boolean tryAcquire(String key, long rate, Duration rateInterval) {
            if (key == null || key.isBlank() || rate < 1L || rateInterval == null || rateInterval.isZero()
                    || rateInterval.isNegative()) {
                return false;
            }
            String slotKey = "rate:" + key;
            long now = clock.millis();
            AtomicBoolean allowed = new AtomicBoolean(false);
            slots.compute(slotKey, (name, current) -> {
                if (current == null || current.expiresAtMillis <= now) {
                    allowed.set(true);
                    return new Slot(1, now + rateInterval.toMillis(), null);
                }
                if (current.value < rate) {
                    allowed.set(true);
                    return new Slot(current.value + 1, current.expiresAtMillis, null);
                }
                return current;
            });
            return allowed.get();
        }
    }

    private final class Blooms implements BloomFilter {
        @Override
        public boolean tryInit(String name, long expectedInsertions, double falsePositiveProbability) {
            if (name == null || name.isBlank() || expectedInsertions < 1L
                    || falsePositiveProbability <= 0D || falsePositiveProbability >= 1D) {
                return false;
            }
            Set<String> previous = blooms.putIfAbsent(name, ConcurrentHashMap.newKeySet());
            return previous == null;
        }

        @Override
        public void add(String name, String value) {
            if (name == null || name.isBlank() || value == null) {
                return;
            }
            blooms.computeIfAbsent(name, ignored -> ConcurrentHashMap.newKeySet()).add(value);
        }

        @Override
        public boolean mightContain(String name, String value) {
            if (name == null || name.isBlank() || value == null) {
                return false;
            }
            Set<String> set = blooms.get(name);
            return set != null && set.contains(value);
        }
    }

    private record Slot(long value, long expiresAtMillis, String text) {
    }

    private record Hold(String token, long expiresAtMillis) {
    }
}
