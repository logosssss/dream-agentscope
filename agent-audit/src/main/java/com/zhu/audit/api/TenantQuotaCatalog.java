package com.zhu.audit.api;

/**
 * 登记租户的每日请求上限。不改已有行，也不记消耗流水。
 */
public interface TenantQuotaCatalog {

    /**
     * 写入 REQUESTS / DAILY。同一租户再写一次是冲突。上限 0 表示不限额。
     */
    TenantQuotaSummary setDailyRequests(String tenantId, Long limit);
}
