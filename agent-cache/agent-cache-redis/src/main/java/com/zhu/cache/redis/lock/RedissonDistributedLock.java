package com.zhu.cache.redis.lock;

import com.zhu.cache.api.lock.DistributedLock;
import com.zhu.cache.redis.RedisCallSupport;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * Redisson 可重入锁。lease 为 null 时走看门狗；失败按 E1 抛不可用。
 */
public class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redisson;

    /**
     * @param redisson 已连上的客户端
     */
    public RedissonDistributedLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
        return RedisCallSupport.call(() -> {
            RLock lock = redisson.getLock(key);
            long waitMs = waitTime == null ? 0L : Math.max(0L, waitTime.toMillis());
            try {
                if (leaseTime == null) {
                    return lock.tryLock(waitMs, TimeUnit.MILLISECONDS);
                }
                long leaseMs = Math.max(1L, leaseTime.toMillis());
                return lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while locking", ex);
            }
        });
    }

    @Override
    public void unlock(String key) {
        RedisCallSupport.run(() -> {
            RLock lock = redisson.getLock(key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        });
    }
}
