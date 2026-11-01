package com.zhu.audit.api;

import java.util.List;

/**
 * 管理进程登记、查看、停用和恢复租户用户。不写角色。
 */
public interface TenantUserCatalog {

    /**
     * 插入一个 ACTIVE 用户。租户必须已经登记。
     * 同一租户再挂这个用户按冲突处理。不写角色。
     */
    TenantUserSummary register(String tenantId, String userId, String displayName);

    /**
     * 把可用用户改成 DISABLED。已经停用再停一次是冲突。
     * 不删数据和会话。
     */
    TenantUserSummary disable(String tenantId, String userId);

    /**
     * 把停用用户改回 ACTIVE。已经可用再恢复一次是冲突。
     * 不写角色，也不删数据。
     */
    TenantUserSummary restore(String tenantId, String userId);

    /**
     * 这个租户下最近登记的用户，含停用。租户必须已经登记。
     * 条数默认由调用方给出，实现侧再压到上限。
     */
    List<TenantUserSummary> recent(String tenantId, int limit);
}
