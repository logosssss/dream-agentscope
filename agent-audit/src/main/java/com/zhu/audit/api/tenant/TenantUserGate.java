package com.zhu.audit.api.tenant;

/**
 * 接入前的租户用户检查。MySQL 关闭时放行，打开时只接受仍可用的登记用户。
 * 这里不写角色，也不停用。
 */
public interface TenantUserGate {

    /**
     * 租户、用户为空、没登记或不是 ACTIVE 时拒绝。
     * MySQL 关闭的实现什么都不做。
     */
    void requireActive(String tenantId, String userId);
}
