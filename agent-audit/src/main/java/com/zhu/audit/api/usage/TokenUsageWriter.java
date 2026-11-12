package com.zhu.audit.api.usage;

/**
 * 一次模型调用的 token 用量。审计模块持有这张表，运行层只调用这个接口。
 */
public interface TokenUsageWriter {

    /**
     * 记下这次的 token。用户或模型为空时跳过。
     * 有当前价格时按每百万 token 计价。没有命中的价格时金额记 0。
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
