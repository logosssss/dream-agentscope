package com.zhu.state.api;

/**
 * 沙箱执行锁。多副本下同一 slot 不能并发操作，锁的实现落在状态层。
 */
public interface ExecutionLockService {

    /**
     * 尝试占用锁。
     *
     * @return 占到返回 true，已被占用返回 false
     */
    boolean tryLock(String lockKey);

    /** 释放当前持有的锁。 */
    void unlock(String lockKey);
}
