package com.zhu.audit.api;

/**
 * 管理进程登记租户用户。只新增，不停用，也不写角色。
 */
public interface TenantUserCatalog {

    /**
     * 插入一个 ACTIVE 用户。租户必须已经登记。
     * 同一租户再挂这个用户按冲突处理。不写角色。
     */
    TenantUserSummary register(String tenantId, String userId, String displayName);
}
