package com.zhu.audit.api;

/**
 * 对话前读取租户的每日上限和同时运行上限。MySQL 关闭时没有这个实现，请求额度用进程配置。
 * 计数不在这里扣，仍由 Redis 完成。
 */
public interface TenantQuotaLookup {

    /**
     * 启用中的每日请求上限。没有行时返回 null。0 表示这个租户不限额。
     */
    Long dailyRequestLimit(String tenantId);

    /**
     * 启用中的每日 token 上限（input+output）。没有行时返回 null。0 表示不限额。
     * 默认没有 token 行，兼容只覆盖请求上限的调用方。
     */
    default Long dailyTokenLimit(String tenantId) {
        return null;
    }

    /**
     * 启用中的每日费用上限，单位是金额微单位（×1e6，与 cost_amount 同精度）。
     * 没有行时返回 null。0 表示不限额。
     */
    default Long dailyCostLimit(String tenantId) {
        return null;
    }

    /**
     * 启用中的同时运行上限。没有行时返回 null。0 表示不限额。
     * 周期是 CURRENT，不是按日。
     */
    default Long concurrentRunLimit(String tenantId) {
        return null;
    }
}
