package com.zhu.audit.api;

/**
 * 管理进程登记和停用租户用户。不恢复，也不写角色。
 */
public interface TenantUserCatalog {

    /**
     * 插入一个 ACTIVE 用户。租户必须已经登记。
     * 同一租户再挂这个用户按冲突处理。不写角色。
     */
    TenantUserSummary register(String tenantId, String userId, String displayName);

    /**
     * 把可用用户改成 DISABLED。已经停用再停一次是冲突。
     * 不删数据和会话，也不恢复。
     */
    TenantUserSummary disable(String tenantId, String userId);
}
