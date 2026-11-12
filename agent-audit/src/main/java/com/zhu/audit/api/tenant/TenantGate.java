package com.zhu.audit.api.tenant;

/**
 * 接入前的租户检查。MySQL 关闭时放行，打开时只接受仍在使用的租户。
 * 这里不校验用户账号。
 */
public interface TenantGate {

    /**
     * 租户为空、不存在或不是 ACTIVE 时拒绝。
     * MySQL 关闭的实现什么都不做。
     */
    void requireActive(String tenantId);
}
