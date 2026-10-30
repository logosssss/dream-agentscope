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
        long now = clock.millis();
        Slot slot = slots.compute(key, (name, current) -> {
            if (current == null || current.expiresAtMillis <= now) {
                return new Slot(1, now + ttl.toMillis());
            }
            return new Slot(current.value + 1, current.expiresAtMillis);
        });
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
                return new Slot(1, now + ttl.toMillis());
            }
            return current;
        });
        return won.get();
    }

    /** 计数值和绝对过期时间。过期时间不随后续增加而延长。 */
    private record Slot(long value, long expiresAtMillis) {
    }
}
