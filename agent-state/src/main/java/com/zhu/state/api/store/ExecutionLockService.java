package com.zhu.state.api.store;

/**
 * 沙箱执行锁。当前走 DistributedStore 的沙箱守卫，本接口还没有实现。
 * 自定义查询出现再实现，不要在旁边再写一套 SQL。
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
