package com.zhu.audit.api;

/**
 * 对话前读取租户的每日请求上限。MySQL 关闭时没有这个实现，额度用进程配置。
 * 计数不在这里扣，仍由 Redis 完成。
 */
public interface TenantQuotaLookup {

    /**
     * 启用中的每日请求上限。没有行时返回 null。0 表示这个租户不限额。
     */
    Long dailyRequestLimit(String tenantId);
}
