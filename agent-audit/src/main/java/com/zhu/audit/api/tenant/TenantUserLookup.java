package com.zhu.audit.api.tenant;

/**
 * 按租户和用户读登记行。给用户进程看自己的显示名和状态。
 * 管理进程不走这个口。不写角色，也不改状态。
 */
public interface TenantUserLookup {

    /**
     * 没有行时返回 null。含停用。空白用户不查库。
     */
    TenantUserSummary find(String tenantId, String userId);
}
