package com.zhu.audit.api;

/**
 * 一次模型调用的 token 用量。审计模块持有这张表，运行层只调用这个接口。
 */
public interface TokenUsageWriter {

    /**
     * 记下这次的 token。用户或模型为空时跳过。
     * 金额由实现决定。当前没有价格表时记 0，不在这里抛计价错误。
     */
    void record(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String model,
            long inputTokens,
            long outputTokens);
}
