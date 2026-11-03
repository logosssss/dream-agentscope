package com.zhu.audit.api;

import java.util.List;

/**
 * 当前用户的 token 用量查询。MySQL 关闭时没有这个实现。
 * 只读已经记下的行，不改金额，也不带提示词。
 */
public interface TokenUsageQuery {

    /**
     * 当前用户在这个租户下最近的用量。默认 50 条，最多 100 条。
     * 不把别人的行带出来。
     */
    List<TokenUsageRow> owned(String userId, String tenantId, int limit);

    /**
     * 当前用户在这个租户下的一条用量。别人的或没有时按找不到。
     * 带记账时间，仍不改金额，也不带提示词。
     */
    TokenUsageDetail ownedOne(String userId, String tenantId, Long usageId);
}
