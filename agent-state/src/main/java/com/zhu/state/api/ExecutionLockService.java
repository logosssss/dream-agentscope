package com.zhu.state.api;

public interface ExecutionLockService {

    boolean tryLock(String lockKey);

    void unlock(String lockKey);
}
