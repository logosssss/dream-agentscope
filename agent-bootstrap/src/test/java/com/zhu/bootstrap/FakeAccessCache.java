package com.zhu.bootstrap;

import com.zhu.access.infrastructure.cache.AccessCache;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 测试用的计数。只在测试源码里，生产进程没有这份实现。
 */
public class FakeAccessCache implements AccessCache {

    private final Clock clock;

    private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Hold> locks = new ConcurrentHashMap<>();

    /** 使用系统时钟。 */
    public FakeAccessCache() {
        this(Clock.systemUTC());
    }

    /** 测试可以拨动时钟，验证过期后计数重新从 1 开始。 */
    public FakeAccessCache(Clock clock) {
        this.clock = clock;
    }

    /** 过期后的下一次增加视为新键。 */
    @Override
    public long increment(String key, Duration ttl) {
        return incrementBy(key, 1L, ttl);
    }

    /** delta 小于 1 时只读。过期后按新键从 delta 起算。 */
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

    /** 负值回拨，小于 0 记成 0。每次都按这次 TTL 重新过期。 */
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

    /** 过期或没有键返回 0。 */
    @Override
    public long getCounter(String key) {
        long now = clock.millis();
        Slot slot = slots.get(key);
        if (slot == null || slot.expiresAtMillis <= now) {
            return 0L;
        }
        return slot.value;
    }

    /**
     * 同一时刻只有一个调用能占住未过期的键。
     * 不能靠过期时间是否相等判断，同一毫秒内的第二次调用会和第一次算出相同时间。
     */
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

    /** 过期后可以重新占。未过期时第二次返回 false。 */
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

    /** 令牌不符或已经过期时留下原记录，不误删下一次的锁。 */
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

    /** 过期后视为没有这个键。 */
    @Override
    public String get(String key) {
        long now = clock.millis();
        Slot slot = slots.get(key);
        if (slot == null || slot.expiresAtMillis <= now || slot.text == null) {
            return null;
        }
        return slot.text;
    }

    /** 覆盖文本并按这次的过期时间计算。 */
    @Override
    public void put(String key, String value, Duration ttl) {
        long now = clock.millis();
        slots.put(key, new Slot(0, now + ttl.toMillis(), value));
    }

    /** 只删文本缓存，不动会话短锁。 */
    @Override
    public void delete(String key) {
        slots.remove(key);
    }

    /** 计数值和绝对过期时间。过期时间不随后续增加而延长。文本缓存和计数分开用。 */
    private record Slot(long value, long expiresAtMillis, String text) {
    }

    /** 会话短锁的令牌和绝对过期时间。 */
    private record Hold(String token, long expiresAtMillis) {
    }
}
