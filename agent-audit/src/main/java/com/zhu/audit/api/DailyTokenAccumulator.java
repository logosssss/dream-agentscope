package com.zhu.audit.api;

/**
 * 对话成功记账后累加当日 token。计数落在 Redis，本缝只负责写入。
 * MySQL 关闭或没有实现时跳过，不影响回复。
 */
public interface DailyTokenAccumulator {

    /**
     * 累加 input+output。tokens 小于 1 时不写。
     * 失败由实现自行吞掉或抛出；调用方不得让回复失败。
     */
    void add(String tenantId, String userId, int tokens);
}
