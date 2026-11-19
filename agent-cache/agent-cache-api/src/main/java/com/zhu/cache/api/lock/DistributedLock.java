package com.zhu.cache.api.lock;

import java.time.Duration;

/**
 * 可重入分布式锁（Redisson）。支持等待与租约；租约为 null 时用看门狗续期。
 * 失败不可用，禁止进程内锁回落。
 */
public interface DistributedLock {

    /**
     * 尝试加锁。
     *
     * @param key       锁键（建议经 {@link com.zhu.cache.api.CacheKeys} 拼出）
     * @param waitTime  最多等待；null 或 0 表示不等待
     * @param leaseTime 持有时长；null 表示看门狗自动续期
     * @return 是否拿到锁
     */
    boolean tryLock(String key, Duration waitTime, Duration leaseTime);

    /**
     * 释放当前线程持有的锁。未持有时实现可忽略或失败，不得静默改用其它后端。
     */
    void unlock(String key);
}
