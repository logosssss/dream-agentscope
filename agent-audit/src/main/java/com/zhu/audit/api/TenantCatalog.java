package com.zhu.audit.api;

/**
 * 管理进程登记、停用和关闭租户。不恢复，也不建用户账号。
 */
public interface TenantCatalog {

    /**
     * 插入一个 ACTIVE 租户。同一标识再登记一次按冲突处理。
     * 套餐固定为 standard。
     */
    TenantSummary register(String tenantId, String name);

    /**
     * 把 ACTIVE 改成 SUSPENDED。找不到按不存在处理。
     * 已经停用或关闭再停一次是冲突。不删数据和用户。
     */
    TenantSummary suspend(String tenantId);

    /**
     * 把尚未关闭的租户改成 CLOSED。已经关闭再关一次是冲突。
     * 不删数据和用户，也不恢复。
     */
    TenantSummary close(String tenantId);
}
