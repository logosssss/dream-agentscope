package com.zhu.audit.api;

import java.util.List;

/**
 * 管理进程登记、查看和改写租户的每日请求上限。不记消耗流水，也不写其它类型。
 */
public interface TenantQuotaCatalog {

    /**
     * 写入 REQUESTS / DAILY。同一租户再写一次是冲突。上限 0 表示不限额。
     */
    TenantQuotaSummary setDailyRequests(String tenantId, Long limit);

    /**
     * 改已有 REQUESTS / DAILY 的上限。没有这一行按不存在处理。
     * 不重置 Redis 已用量，也不改类型和周期。
     */
    TenantQuotaSummary updateDailyRequests(String tenantId, Long limit);

    /**
     * 最近登记的每日请求上限。条数默认由调用方给出，实现侧再压到上限。
     * 不带回已用量。
     */
    List<TenantQuotaSummary> recent(int limit);
}
