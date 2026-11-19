package com.zhu.cache.api.counter;

import java.time.Duration;

/**
 * 计数缓存。额度、限流、并发占位等只加减数字；失败须不可用，禁止内存回落。
 */
public interface CounterCache {

    /**
     * 计数加一。键首次出现时设定过期，之后增加不延长过期。
     *
     * @return 增加后的值，从 1 起
     */
    long increment(String key, Duration ttl);

    /**
     * 计数加 delta。delta 小于 1 时不改键，返回当前值。
     * 键首次出现时设定过期，之后增加不延长过期。
     */
    long incrementBy(String key, long delta, Duration ttl);

    /**
     * 计数加 delta（可为负）。结果小于 0 时写成 0。
     * 每次刷新过期。delta 为 0 时只读。
     */
    long add(String key, long delta, Duration ttl);

    /**
     * 读当前计数。无键或已过期返回 0。
     */
    long getCounter(String key);
}
