package com.zhu.audit.api;

/**
 * 对话成功计价后累加当日费用。计数落在 Redis，单位是金额微单位（×1e6）。
 * MySQL 关闭或没有实现时跳过，不影响回复。
 */
public interface DailyCostAccumulator {

    /**
     * 累加本次费用微单位。micros 小于 1 时不写。
     * 失败由实现自行吞掉或抛出；调用方不得让回复失败。
     */
    void add(String tenantId, String userId, long micros);
}
