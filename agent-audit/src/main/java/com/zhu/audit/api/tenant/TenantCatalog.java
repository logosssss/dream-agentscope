package com.zhu.audit.api.tenant;

import java.util.List;

/**
 * 管理进程登记、查看、停用、关闭和恢复租户。不建用户账号，也不改用户状态。
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
     * 不删数据和用户。
     */
    TenantSummary close(String tenantId);

    /**
     * 把停用或关闭的租户改回 ACTIVE。已经可用再恢复一次是冲突。
     * 不改用户状态，也不删数据。
     */
    TenantSummary restore(String tenantId);

    /**
     * 最近登记的租户，含停用和关闭。条数默认由调用方给出，实现侧再压到上限。
     */
    List<TenantSummary> recent(int limit);
}
