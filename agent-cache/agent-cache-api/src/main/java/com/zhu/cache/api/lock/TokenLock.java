package com.zhu.cache.api.lock;

import java.time.Duration;

/**
 * 基于 SETNX 的短锁 / 幂等占位。令牌释放；无看门狗。复杂锁用 {@link DistributedLock}。
 */
public interface TokenLock {

    /**
     * 无令牌占位（值固定为 {@code 1}）。已占用返回 false。
     */
    boolean claim(String key, Duration ttl);

    /**
     * 用令牌占住。已占用返回 false。令牌用于 {@link #unlock}。
     */
    boolean lock(String key, String token, Duration ttl);

    /**
     * 仅令牌匹配时释放。过期或令牌不符则无操作。
     */
    void unlock(String key, String token);
}
