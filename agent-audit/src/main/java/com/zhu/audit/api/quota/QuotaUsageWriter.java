package com.zhu.audit.api.quota;

/**
 * 记下一次已经通过的每日请求。MySQL 关闭时没有这个实现。
 * 不回补，也不关联还没开始的运行。
 */
public interface QuotaUsageWriter {

    /**
     * 写一行 REQUESTS，数量是 1。写失败由调用方吞掉，不能把已经扣过的请求退回。
     */
    void recordDailyRequest(String tenantId, String userId, String reason);
}
