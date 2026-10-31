package com.zhu.audit.api;

/**
 * 管理进程登记租户。只新增，不在这里停用，也不建用户账号。
 */
public interface TenantCatalog {

    /**
     * 插入一个 ACTIVE 租户。同一标识再登记一次按冲突处理。
     * 套餐固定为 standard。
     */
    TenantSummary register(String tenantId, String name);
}
